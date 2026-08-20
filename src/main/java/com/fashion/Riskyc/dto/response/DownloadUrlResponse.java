package com.fashion.Riskyc.dto.response;

import java.time.Instant;

public record DownloadUrlResponse(
        String url,
        Instant expiresAt
) {
}
