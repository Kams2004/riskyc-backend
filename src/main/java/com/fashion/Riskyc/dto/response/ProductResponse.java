package com.fashion.Riskyc.dto.response;

import com.fashion.Riskyc.entity.Badge;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        BigDecimal originalPrice,
        String categorySlug,
        String subcategorySlug,
        List<String> sizes,
        List<String> tags,
        Double rating,
        Integer reviews,
        Badge badge,
        boolean hidden,
        List<ProductColorResponse> colors,
        /** Presigned URLs already resolved — zero extra round-trips for the client. */
        List<MediaResponse> media,
        Instant createdAt,
        Instant updatedAt
) {
}
