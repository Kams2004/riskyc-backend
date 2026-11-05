package com.fashion.Riskyc.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PosDeviceResponse(
        UUID id,
        String name,
        String platform,
        String appVersion,
        Instant enrolledAt,
        String enrolledByName,
        Instant lastSeenAt,
        /** Checked in within the last ONLINE_WINDOW (see PosDeviceService). */
        boolean online,
        int pendingSales,
        Instant revokedAt
) {
}
