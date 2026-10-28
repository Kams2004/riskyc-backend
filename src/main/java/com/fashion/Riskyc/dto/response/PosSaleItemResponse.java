package com.fashion.Riskyc.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record PosSaleItemResponse(
        UUID id,
        UUID productId,
        String productName,
        String thumbnailUrl,
        Integer quantity,
        String selectedColor,
        String selectedSize,
        BigDecimal unitPrice
) {
}
