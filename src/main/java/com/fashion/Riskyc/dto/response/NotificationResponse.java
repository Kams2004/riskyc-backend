package com.fashion.Riskyc.dto.response;

import com.fashion.Riskyc.entity.NotificationRecipient;
import com.fashion.Riskyc.entity.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationRecipient recipientType,
        UUID customerId,
        NotificationType type,
        String message,
        String referenceId,
        boolean read,
        Instant createdAt
) {
}
