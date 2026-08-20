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
        Instant timestamp
) {
}
