package com.fashion.Riskyc.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AttachCustomerRequest(
        @NotNull UUID customerId
) {
}
