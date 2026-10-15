package com.fashion.Riskyc.dto.response;

import com.fashion.Riskyc.entity.MediaType;

import java.time.Instant;
import java.util.UUID;

/**
 * A publicity carousel slide with its presigned download URL already
 * resolved server-side — same shape/reasoning as {@link MediaResponse}.
 */
public record PublicityItemResponse(
        UUID id,
        String presignedUrl,
        MediaType type,
        String caption,
        Integer position,
        boolean active,
        Instant uploadedAt
) {
}
