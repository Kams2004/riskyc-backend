package com.fashion.Riskyc.dto.response;

import java.math.BigDecimal;

public record BulkPriceTierResponse(
        Integer quantity,
        BigDecimal price
) {
}
