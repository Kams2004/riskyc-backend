package com.fashion.Riskyc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ExpoPushSubscribeRequest(
        @NotNull UUID orderId,
        @NotBlank String token
) {
}
