package com.fashion.Riskyc.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CheckAvailabilityRequest(@NotEmpty @Valid List<OrderItemRequest> items) {
}
