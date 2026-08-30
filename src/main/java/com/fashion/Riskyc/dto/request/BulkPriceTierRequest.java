package com.fashion.Riskyc.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record BulkPriceTierRequest(
        @NotNull @Positive Integer quantity,
        @NotNull @Positive BigDecimal price
) {
}
