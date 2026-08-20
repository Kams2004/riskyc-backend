package com.fashion.Riskyc.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        UUID productId,
        String productName,
        /** First product image, presigned — enough for an order-line thumbnail. */
        String productThumbnailUrl,
        Integer quantity,
        String selectedColor,
        String selectedSize,
        BigDecimal unitPrice
) {
}
