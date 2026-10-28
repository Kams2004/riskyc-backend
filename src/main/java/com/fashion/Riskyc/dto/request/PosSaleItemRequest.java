package com.fashion.Riskyc.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/** selectedColor/selectedSize default to "" client-side for a product with no color/size options — never null, matching ProductVariant's own convention. */
public record PosSaleItemRequest(
        @NotNull UUID productId,
        @NotNull @Positive Integer quantity,
        @NotNull String selectedColor,
        @NotNull String selectedSize
) {
}
