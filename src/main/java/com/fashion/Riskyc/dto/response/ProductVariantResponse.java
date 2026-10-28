package com.fashion.Riskyc.dto.response;

import java.util.UUID;

/** colorName/size are "" when the product has no color/size options — mirrors OrderItemResponse's own convention. */
public record ProductVariantResponse(
        UUID id,
        String colorName,
        String size,
        Integer quantity,
        boolean untracked
) {
}
