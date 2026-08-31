package com.fashion.Riskyc.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Broadcast over /topic/conversations/{id}/read whenever either side opens the thread, so the other side's tick marks update live. */
public record ConversationReadStatusResponse(
        UUID conversationId,
        Instant customerReadAt,
        Instant adminReadAt
) {
}
