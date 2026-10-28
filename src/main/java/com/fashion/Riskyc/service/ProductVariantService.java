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
     * Locks and resolves the variant for one order/sale line, creating it
     * (quantity 0, untracked false) if this exact color+size combination
     * has never been seen before — e.g. an admin added a new size after
     * this feature shipped, without yet visiting the stock editor. Starting
     * at 0 (sold out) rather than unlimited is deliberate: a combination
     * nobody has stocked should not be silently orderable.
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
                        .build()));
    }

    /** Online checkout path — rejects outright if there isn't enough, unless this variant is still flagged "untracked" (migration sentinel), in which case it behaves like the old stock==null convention: always allowed. */
    public void decrementStrict(ProductVariant variant, int quantity) {
        if (!variant.isUntracked() && variant.getQuantity() < quantity) {
            throw new ConflictException(describe(variant) + " — only " + variant.getQuantity() + " left");
        }
        variant.setQuantity(variant.getQuantity() - quantity);
    }

    /** POS offline-sync path — a completed in-person sale is a historical fact and is never rejected or undone on stock grounds; returns true if this decrement pushed the variant negative, so the caller can flag the sale for reconciliation. */
    public boolean decrementAllowingNegative(ProductVariant variant, int quantity) {
        variant.setQuantity(variant.getQuantity() - quantity);
        return variant.getQuantity() < 0;
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
