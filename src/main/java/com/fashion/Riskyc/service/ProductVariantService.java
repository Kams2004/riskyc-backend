package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.request.ProductVariantStockEntryRequest;
import com.fashion.Riskyc.dto.response.ProductVariantResponse;
import com.fashion.Riskyc.entity.Product;
import com.fashion.Riskyc.entity.ProductVariant;
import com.fashion.Riskyc.exception.ConflictException;
import com.fashion.Riskyc.exception.ResourceNotFoundException;
import com.fashion.Riskyc.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The real stock ledger service — see {@link ProductVariant}'s own javadoc
 * for why this exists instead of reusing {@code ProductColor.stock}.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProductVariantService {

    private final ProductVariantRepository variantRepository;

    @Transactional(readOnly = true)
    public List<ProductVariantResponse> listForProduct(UUID productId) {
        return variantRepository.findByProductIdOrderByColorNameAscSizeAsc(productId).stream().map(this::toResponse).toList();
    }

    /** Admin correction — sets the quantity directly and clears "untracked" (an admin typing a real number is exactly what turns a migration-backfilled sentinel into a trusted figure). */
    public ProductVariantResponse upsertQuantity(Product product, ProductVariantStockEntryRequest entry) {
        ProductVariant variant = findOrCreate(product, entry.colorName(), entry.size());
        variant.setQuantity(entry.quantity());
        variant.setUntracked(false);
        return toResponse(variantRepository.save(variant));
    }

    /**
     * Locks and resolves the variant for one order/sale line, creating it if
     * this exact color+size combination has never been seen before. On a
     * product whose stock is tracked, a new combination starts at 0 (sold
     * out) — nobody has stocked it. On a product that never had any
     * quantity entered, it's created untracked (always available), matching
     * how the storefront and POS already display such products.
     */
    public ProductVariant resolveForUpdate(Product product, String colorName, String size) {
        ProductVariant existing = findOrCreate(product, colorName, size);
        return variantRepository.findByIdForUpdate(existing.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("ProductVariant", existing.getId()));
    }

    private ProductVariant findOrCreate(Product product, String colorName, String size) {
        String color = colorName != null ? colorName : "";
        String sz = size != null ? size : "";
        return variantRepository.findByProductIdAndColorNameAndSize(product.getId(), color, sz)
                .orElseGet(() -> variantRepository.saveAndFlush(ProductVariant.builder()
                        .product(product)
                        .colorName(color)
                        .size(sz)
                        .quantity(0)
                        .untracked(!variantRepository.existsByProductIdAndUntrackedFalse(product.getId()))
                        .build()));
    }

    // Untracked (unlimited) variants are never counted down or up, so their stored quantity stays a meaningless 0
    // instead of drifting negative — admin stock editors would otherwise show, and could save, that figure.

    /** Online checkout path — rejects outright if there isn't enough; an untracked variant is always allowed. */
    public void decrementStrict(ProductVariant variant, int quantity) {
        if (variant.isUntracked()) return;
        if (variant.getQuantity() < quantity) {
            throw new ConflictException(describe(variant) + " — only " + variant.getQuantity() + " left");
        }
        variant.setQuantity(variant.getQuantity() - quantity);
    }

    /** POS offline-sync path — a completed in-person sale is a historical fact and is never rejected or undone on stock grounds; returns true if this decrement pushed a tracked variant negative, so the caller can flag the sale for reconciliation. */
    public boolean decrementAllowingNegative(ProductVariant variant, int quantity) {
        if (variant.isUntracked()) return false;
        variant.setQuantity(variant.getQuantity() - quantity);
        return variant.getQuantity() < 0;
    }

    /**
     * Read-only stock check used before a customer pays — same rule as resolveForUpdate + decrementStrict, but it
     * never locks or creates a row. Throws the same "Sold out" message checkout would.
     */
    @Transactional(readOnly = true)
    public void requireAvailable(Product product, String colorName, String size, int quantity) {
        String color = colorName != null ? colorName : "";
        String sz = size != null ? size : "";
        ProductVariant variant = variantRepository.findByProductIdAndColorNameAndSize(product.getId(), color, sz).orElse(null);
        if (variant == null) {
            if (!variantRepository.existsByProductIdAndUntrackedFalse(product.getId())) return;
            variant = ProductVariant.builder().colorName(color).size(sz).quantity(0).build();
        }
        if (!variant.isUntracked() && variant.getQuantity() < quantity) {
            throw new ConflictException(describe(variant) + " — only " + Math.max(0, variant.getQuantity()) + " left");
        }
    }

    /** Puts stock back after a voided/cancelled sale. */
    public void restock(ProductVariant variant, int quantity) {
        if (variant.isUntracked()) return;
        variant.setQuantity(variant.getQuantity() + quantity);
    }

    public void deleteAllForProduct(UUID productId) {
        variantRepository.deleteByProductId(productId);
    }

    private String describe(ProductVariant v) {
        StringBuilder sb = new StringBuilder("Sold out");
        if (!v.getColorName().isEmpty()) sb.append(" (").append(v.getColorName());
        if (!v.getSize().isEmpty()) sb.append(v.getColorName().isEmpty() ? " (" : ", ").append(v.getSize());
        if (!v.getColorName().isEmpty() || !v.getSize().isEmpty()) sb.append(")");
        return sb.toString();
    }

    private ProductVariantResponse toResponse(ProductVariant v) {
        return new ProductVariantResponse(v.getId(), v.getColorName(), v.getSize(), v.getQuantity(), v.isUntracked());
    }
}
