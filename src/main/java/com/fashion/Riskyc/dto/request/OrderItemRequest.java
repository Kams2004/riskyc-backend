package com.fashion.Riskyc.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record OrderItemRequest(
        @NotNull UUID productId,
        @NotNull @Positive Integer quantity,
        String selectedColor,
        String selectedSize
) {
}
