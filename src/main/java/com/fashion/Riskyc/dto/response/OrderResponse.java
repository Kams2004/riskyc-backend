package com.fashion.Riskyc.dto.response;

import com.fashion.Riskyc.entity.OrderStatus;
import com.fashion.Riskyc.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID customerId,
        List<OrderItemResponse> items,
        BigDecimal total,
        OrderStatus status,
        CustomerInfoResponse customerInfo,
        PaymentMethod paymentMethod,
        String paymentCode,
        /** Presigned URL for the uploaded payment proof, if any — resolved server-side. */
        String paymentScreenshotUrl,
        Instant createdAt,
        Instant updatedAt
) {
}
