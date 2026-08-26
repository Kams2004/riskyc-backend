package com.fashion.Riskyc.dto.response;

import com.fashion.Riskyc.entity.MediaType;

import java.time.Instant;
import java.util.UUID;

/**
 * A product image/video with its presigned download URL already resolved
 * server-side — the frontend never has to make a follow-up call per media
 * item to find out where to fetch it from.
 */
public record MediaResponse(
        UUID id,
        String presignedUrl,
        MediaType type,
        String contentType,
        Long sizeBytes,
        Instant uploadedAt
) {
}
