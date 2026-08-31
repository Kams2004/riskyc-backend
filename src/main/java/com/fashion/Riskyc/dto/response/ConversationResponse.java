package com.fashion.Riskyc.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        String customerName,
        UUID customerId,
        UUID orderId,
        List<ChatMessageResponse> messages,
        int unread,
        Instant createdAt,
        Instant lastMessageAt,
        Instant customerReadAt,
        Instant adminReadAt
) {
}
