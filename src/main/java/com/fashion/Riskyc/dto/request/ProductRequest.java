package com.fashion.Riskyc.dto.request;

import com.fashion.Riskyc.entity.Badge;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/** Used for both create and update — a full replace of the product's editable fields. */
public record ProductRequest(
        @NotBlank String name,
        /** Optional — an admin can publish a product without a description. */
        String description,
        @NotNull @Positive BigDecimal price,
        BigDecimal originalPrice,
        @NotBlank String categorySlug,
        String subcategorySlug,
        List<String> sizes,
        List<String> tags,
        Badge badge,
        Boolean hidden,
        Double rating,
        Integer reviews,
        @NotEmpty @Valid List<ProductColorRequest> colors
) {
}
