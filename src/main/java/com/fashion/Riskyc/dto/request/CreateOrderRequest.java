package com.fashion.Riskyc.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        /** Null for a guest checkout. */
        UUID customerId,
        @NotEmpty @Valid List<OrderItemRequest> items,
        @Valid CustomerInfoRequest customerInfo
) {
}
