package com.fashion.Riskyc.dto.request;

import com.fashion.Riskyc.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @NotNull OrderStatus status
) {
}
