package com.fashion.Riskyc.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ProductAuditLogResponse(
        UUID id,
        UUID productId,
        String productName,
        String section,
        String summary,
        String changedByName,
        Instant changedAt
) {
}
