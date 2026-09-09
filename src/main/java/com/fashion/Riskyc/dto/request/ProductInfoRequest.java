package com.fashion.Riskyc.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Name, description, category/subcategory — the UPDATE_PRODUCT_INFO section of the edit form. */
public record ProductInfoRequest(
        @NotBlank String name,
        String description,
        @NotBlank String categorySlug,
        String subcategorySlug
) {
}
