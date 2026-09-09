package com.fashion.Riskyc.dto.request;

import com.fashion.Riskyc.entity.Badge;

import java.util.List;

/** Badge, sizes, rating, review count — the UPDATE_PRODUCT_DISPLAY section of the edit form (visibility is its own, separate permission — see the /visibility endpoint). */
public record ProductDisplayRequest(
        Badge badge,
        List<String> sizes,
        Double rating,
        Integer reviews
) {
}
