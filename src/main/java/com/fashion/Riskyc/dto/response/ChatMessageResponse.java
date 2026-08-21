package com.fashion.Riskyc.dto.response;

import com.fashion.Riskyc.entity.MessageSender;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        UUID conversationId,
        MessageSender sender,
        String text,
        String imageUrl,
        /** Staff member who sent this, when sender is ADMIN — never expose this to the customer widget. */
        String adminSenderName,
        Instant timestamp
) {
}
