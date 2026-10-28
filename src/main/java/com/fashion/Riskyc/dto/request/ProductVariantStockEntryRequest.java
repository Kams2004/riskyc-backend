package com.fashion.Riskyc.dto.request;

import jakarta.validation.constraints.NotNull;

/** colorName/size default to "" client-side for a product with no color/size options — never null. */
public record ProductVariantStockEntryRequest(
        @NotNull String colorName,
        @NotNull String size,
        @NotNull Integer quantity
) {
}
