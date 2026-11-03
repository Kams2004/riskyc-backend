package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.request.CreatePosSaleRequest;
import com.fashion.Riskyc.dto.request.PosSaleItemRequest;
import com.fashion.Riskyc.dto.response.PosReconciliationLineResponse;
import com.fashion.Riskyc.dto.response.PosSaleItemResponse;
import com.fashion.Riskyc.dto.response.PosSaleResponse;
import com.fashion.Riskyc.entity.Enterprise;
import com.fashion.Riskyc.entity.PosSale;
import com.fashion.Riskyc.entity.PosSaleItem;
import com.fashion.Riskyc.entity.Product;
import com.fashion.Riskyc.entity.ProductVariant;
import com.fashion.Riskyc.exception.BadRequestException;
import com.fashion.Riskyc.exception.ConflictException;
import com.fashion.Riskyc.exception.ForbiddenException;
import com.fashion.Riskyc.exception.ResourceNotFoundException;
import com.fashion.Riskyc.repository.EnterpriseRepository;
import com.fashion.Riskyc.repository.PosSaleItemRepository;
import com.fashion.Riskyc.repository.PosSaleRepository;
import com.fashion.Riskyc.repository.ProductRepository;
import com.fashion.Riskyc.security.CurrentAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The POS sale pipeline — the in-person analogue of OrderService, with one
 * deliberate difference: a sale here is never rejected or rolled back on
 * stock grounds. By the time this method runs, cash has already changed
 * hands (possibly hours ago, if the till was offline) — stock can only go
 * negative and get flagged for reconciliation, never undo the sale.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PosSaleService {

    private static final String RECEIPT_FOLDER = "pos-receipts";

    private final PosSaleRepository posSaleRepository;
    private final PosSaleItemRepository posSaleItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantService productVariantService;
    private final S3MediaService s3MediaService;
    private final PricingService pricingService;
    private final EnterpriseRepository enterpriseRepository;

    /** Platform staff see every sale; a scoped admin sees only their own enterprise's. */
    @Transactional(readOnly = true)
    public List<PosSaleResponse> listAll() {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        List<PosSale> sales = scopeId == null
                ? posSaleRepository.findAllByOrderBySoldAtDesc()
                : posSaleRepository.findByEnterpriseIdOrderBySoldAtDesc(scopeId);
        return sales.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PosSaleResponse> listFlagged() {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        List<PosSale> sales = scopeId == null
                ? posSaleRepository.findAllByStockReconciliationFlagTrueOrderBySoldAtDesc()
                : posSaleRepository.findByEnterpriseIdAndStockReconciliationFlagTrueOrderBySoldAtDesc(scopeId);
        return sales.stream().map(this::toResponse).toList();
    }

    /**
     * Every oversold line across every sale (voided sales excluded — their
     * stock has already been restored) — a handful of sales at most, so one
     * in-memory pass over the already-small POS sales table beats a second
     * bespoke query. Reconciled and still-pending lines come back together;
     * the frontend splits them into tabs. Scoped the same way listAll() is.
     */
    @Transactional(readOnly = true)
    public List<PosReconciliationLineResponse> listReconciliationLines() {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        List<PosSale> sales = scopeId == null
                ? posSaleRepository.findAllByOrderBySoldAtDesc()
                : posSaleRepository.findByEnterpriseIdOrderBySoldAtDesc(scopeId);
        return sales.stream()
                .filter(sale -> !sale.isVoided())
                .flatMap(sale -> sale.getItems().stream()
                        .filter(item -> item.getStockBeforeSale() != null && item.getQuantity() > item.getStockBeforeSale())
                        .map(item -> toReconciliationLine(sale, item)))
                .toList();
    }

    /** Marks one oversold line reviewed/handled — an audit-trail marker only; it never touches stock itself (that's the Products stock editor's job). */
    public PosReconciliationLineResponse reconcileItem(UUID itemId) {
        PosSaleItem item = posSaleItemRepository.findById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("PosSaleItem", itemId));
        requireOwnedOrPlatform(item.getSale());
        if (item.getStockBeforeSale() == null || item.getQuantity() <= item.getStockBeforeSale()) {
            throw new BadRequestException("This line was never oversold — nothing to reconcile.");
        }
        if (item.isReconciled()) {
            throw new ConflictException("Already reconciled");
        }
        item.setReconciled(true);
        item.setReconciledAt(Instant.now());
        item.setReconciledByName(CurrentAdmin.nameOrNull());
        return toReconciliationLine(item.getSale(), item);
    }

    /**
     * Idempotent by clientSaleId: a retried sync (after a partial network
     * failure on the device's first attempt) returns the already-persisted
     * row unchanged instead of erroring or decrementing stock a second time.
     */
    public PosSaleResponse create(CreatePosSaleRequest request) {
        var existing = posSaleRepository.findByClientSaleId(request.clientSaleId());
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();

        List<Product> productsByIndex = new ArrayList<>(request.items().size());
        Map<UUID, List<Integer>> indicesByProduct = new LinkedHashMap<>();
        for (int i = 0; i < request.items().size(); i++) {
            PosSaleItemRequest item = request.items().get(i);
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new BadRequestException("Product not found: " + item.productId()));
            // A scoped seller's till can only ever ring up their own
            // inventory — selling another seller's product isn't a
            // visibility nuance like the storefront's hidden/suspended
            // filtering, it's a different business's stock and cash
            // register entirely. Platform staff keep today's unrestricted
            // behavior (the flagship-store till).
            if (scopeId != null && (product.getEnterprise() == null || !scopeId.equals(product.getEnterprise().getId()))) {
                throw new ForbiddenException("\"" + product.getName() + "\" belongs to a different seller.");
            }
            productsByIndex.add(product);
            indicesByProduct.computeIfAbsent(product.getId(), k -> new ArrayList<>()).add(i);
        }

        // Same grouped/tiered pricing the storefront checkout applies (see
        // PricingService) — a cashier ringing up several lines of the same
        // product (e.g. one per size) still gets the combined-quantity
        // discount tier, not each line falling short of it individually.
        BigDecimal[] lineTotals = new BigDecimal[request.items().size()];
        for (List<Integer> indices : indicesByProduct.values()) {
            Product product = productsByIndex.get(indices.get(0));
            List<Integer> quantities = indices.stream().map(i -> request.items().get(i).quantity()).toList();
            List<BigDecimal> allocated = pricingService.allocateGroupedLineTotals(product.getPrice(), product.getBulkPrices(), quantities);
            for (int j = 0; j < indices.size(); j++) {
                lineTotals[indices.get(j)] = allocated.get(j);
            }
        }

        // Lock variants in a stable order across the whole sale — same
        // deadlock-avoidance reasoning as OrderService.create(), even though
        // a decrement here can never itself be rejected.
        List<Integer> lockOrder = new ArrayList<>();
        for (int i = 0; i < request.items().size(); i++) lockOrder.add(i);
        lockOrder.sort(Comparator.<Integer, UUID>comparing(i -> productsByIndex.get(i).getId())
                .thenComparing(i -> request.items().get(i).selectedColor())
                .thenComparing(i -> request.items().get(i).selectedSize()));

        boolean anyNegative = false;
        Integer[] stockBeforeByIndex = new Integer[request.items().size()];
        for (int i : lockOrder) {
            PosSaleItemRequest item = request.items().get(i);
            Product product = productsByIndex.get(i);
            ProductVariant variant = productVariantService.resolveForUpdate(product, item.selectedColor(), item.selectedSize());
            stockBeforeByIndex[i] = variant.getQuantity();
            if (productVariantService.decrementAllowingNegative(variant, item.quantity())) {
                anyNegative = true;
            }
        }

        PosSale sale = PosSale.builder()
                .clientSaleId(request.clientSaleId())
                .cashierId(CurrentAdmin.idOrNull())
                .cashierName(CurrentAdmin.nameOrNull() != null ? CurrentAdmin.nameOrNull() : "Unknown")
                .paymentMethod(request.paymentMethod())
                .soldAt(request.soldAt())
                .stockReconciliationFlag(anyNegative)
                .total(BigDecimal.ZERO)
                .enterprise(resolveSaleEnterprise(scopeId, productsByIndex))
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < request.items().size(); i++) {
            PosSaleItemRequest itemReq = request.items().get(i);
            Product product = productsByIndex.get(i);
            BigDecimal lineTotal = lineTotals[i];
            total = total.add(lineTotal);
            BigDecimal effectiveUnitPrice = lineTotal.divide(BigDecimal.valueOf(itemReq.quantity()), 2, RoundingMode.HALF_UP);
            sale.getItems().add(PosSaleItem.builder()
                    .sale(sale)
                    .product(product)
                    .productName(product.getName())
                    .quantity(itemReq.quantity())
                    .selectedColor(itemReq.selectedColor())
                    .selectedSize(itemReq.selectedSize())
                    .unitPrice(effectiveUnitPrice)
                    .stockBeforeSale(stockBeforeByIndex[i])
                    .build());
        }
        sale.setTotal(total);

        PosSale saved = posSaleRepository.saveAndFlush(sale);
        return toResponse(saved);
    }

    public PosSaleResponse uploadReceipt(UUID saleId, MultipartFile file) {
        PosSale sale = getOrThrow(saleId);
        requireOwnedOrPlatform(sale);
        String key = s3MediaService.upload(file, RECEIPT_FOLDER + "/" + saleId);
        sale.setReceiptKey(key);
        return toResponse(sale);
    }

    /**
     * Reverses a completed sale: restores every line's stock and marks the
     * sale voided (never deleted, so it stays visible in reporting). Unlike
     * create(), this CAN be refused — VOID_POS_SALE is its own permission so
     * a cashier can ring up sales without being able to undo one.
     */
    public PosSaleResponse voidSale(UUID saleId) {
        PosSale sale = getOrThrow(saleId);
        requireOwnedOrPlatform(sale);
        if (sale.isVoided()) {
            throw new ConflictException("Already voided");
        }
        for (PosSaleItem item : sale.getItems()) {
            if (item.getProduct() == null) continue; // product since deleted — nothing to restore stock against
            ProductVariant variant = productVariantService.resolveForUpdate(item.getProduct(), item.getSelectedColor(), item.getSelectedSize());
            variant.setQuantity(variant.getQuantity() + item.getQuantity());
        }
        sale.setVoided(true);
        sale.setVoidedByName(CurrentAdmin.nameOrNull());
        sale.setVoidedAt(Instant.now());
        return toResponse(sale);
    }

    private PosSale getOrThrow(UUID id) {
        return posSaleRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("PosSale", id));
    }

    private void requireOwnedOrPlatform(PosSale sale) {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        if (scopeId == null) return;
        if (sale.getEnterprise() == null || !scopeId.equals(sale.getEnterprise().getId())) {
            throw new ForbiddenException("This sale belongs to a different seller.");
        }
    }

    /**
     * A scoped admin's sale is always restricted (by create()'s own guard
     * above) to their own enterprise's products, so this is just resolving
     * that one Enterprise. A platform admin's sale has no such restriction —
     * if every item happens to share one enterprise, record it (useful for
     * reporting); if it spans more than one, there's no single answer, so
     * it's left null rather than guessed at.
     */
    private Enterprise resolveSaleEnterprise(UUID scopeId, List<Product> productsByIndex) {
        if (scopeId != null) {
            return enterpriseRepository.findById(scopeId).orElseThrow(() -> ResourceNotFoundException.of("Enterprise", scopeId));
        }
        Enterprise first = productsByIndex.isEmpty() ? null : productsByIndex.get(0).getEnterprise();
        if (first == null) return null;
        boolean allSame = productsByIndex.stream()
                .allMatch(p -> p.getEnterprise() != null && p.getEnterprise().getId().equals(first.getId()));
        return allSame ? first : null;
    }

    private PosSaleResponse toResponse(PosSale sale) {
        List<PosSaleItemResponse> items = sale.getItems().stream().map(this::toResponse).toList();
        String receiptUrl = sale.getReceiptKey() != null ? s3MediaService.getPresignedUrl(sale.getReceiptKey()) : null;
        return new PosSaleResponse(
                sale.getId(),
                sale.getClientSaleId(),
                sale.getCashierId(),
                sale.getCashierName(),
                sale.getEnterprise() != null ? sale.getEnterprise().getId() : null,
                items,
                sale.getTotal(),
                sale.getPaymentMethod(),
                receiptUrl,
                sale.getSoldAt(),
                sale.getCreatedAt(),
                sale.isStockReconciliationFlag(),
                sale.isVoided(),
                sale.getVoidedByName(),
                sale.getVoidedAt()
        );
    }

    private PosReconciliationLineResponse toReconciliationLine(PosSale sale, PosSaleItem item) {
        Product product = item.getProduct();
        String thumbnailUrl = null;
        if (product != null && !product.getMedia().isEmpty()) {
            thumbnailUrl = s3MediaService.getPresignedUrl(product.getMedia().get(0).getStorageKey());
        }
        int oversoldBy = item.getQuantity() - item.getStockBeforeSale();
        return new PosReconciliationLineResponse(
                item.getId(),
                sale.getId(),
                sale.getSoldAt(),
                sale.getCashierName(),
                item.getProductName(),
                thumbnailUrl,
                item.getSelectedColor(),
                item.getSelectedSize(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getStockBeforeSale(),
                item.getStockBeforeSale() - item.getQuantity(),
                oversoldBy,
                item.isReconciled(),
                item.getReconciledAt(),
                item.getReconciledByName()
        );
    }

    private PosSaleItemResponse toResponse(PosSaleItem item) {
        Product product = item.getProduct();
        String thumbnailUrl = null;
        if (product != null && !product.getMedia().isEmpty()) {
            thumbnailUrl = s3MediaService.getPresignedUrl(product.getMedia().get(0).getStorageKey());
        }
        return new PosSaleItemResponse(
                item.getId(),
                product != null ? product.getId() : null,
                item.getProductName(),
                thumbnailUrl,
                item.getQuantity(),
                item.getSelectedColor(),
                item.getSelectedSize(),
                item.getUnitPrice()
        );
    }
}
