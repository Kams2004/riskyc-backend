package com.fashion.Riskyc.dto.request;

import com.fashion.Riskyc.entity.Badge;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

/** Used for both create and update — a full replace of the product's editable fields. */
public record ProductRequest(
        @NotBlank String name,
        /** Optional — an admin can publish a product without a description. */
        String description,
        /** Optional — the frontend sends zero (not null) for "not set yet" / "price on request". */
        @PositiveOrZero BigDecimal price,
        BigDecimal originalPrice,
        @NotBlank String categorySlug,
        String subcategorySlug,
        List<String> sizes,
        List<String> tags,
        Badge badge,
        Boolean hidden,
        Double rating,
        Integer reviews,
        /** Optional — a product can be published without any color variants defined yet. */
        @Valid List<ProductColorRequest> colors,
        /** Optional "buy N for this total" tiers, independent of the regular unit price. */
        @Valid List<BulkPriceTierRequest> bulkPrices
) {
}
