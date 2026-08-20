package com.fashion.Riskyc.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CreateConversationRequest(
        @NotBlank String customerName,
        UUID customerId,
        UUID orderId
) {
}
