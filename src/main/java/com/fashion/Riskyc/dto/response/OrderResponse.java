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
        String paymentAccountName,
        /** Presigned URL for the uploaded payment proof, if any — resolved server-side. */
        String paymentScreenshotUrl,
        String statusChangedByName,
        Instant statusChangedAt,
        String rejectionReason,
        String packagingStartedByName,
        UUID packagingStartedById,
        Instant packagingStartedAt,
        String packagingCompletedByName,
        UUID packagingCompletedById,
        Instant packagingCompletedAt,
        /** The latest packaging-confirmation message sent for this order (see ConversationService), if any — null until an admin sends one. */
        ChatMessageResponse packagingConfirmation,
        Instant createdAt,
        Instant updatedAt
) {
}
