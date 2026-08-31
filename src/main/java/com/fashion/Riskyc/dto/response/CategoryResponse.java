package com.fashion.Riskyc.dto.response;

import java.util.List;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String slug,
        String name,
        /** Machine-translated French version — null until a translation has succeeded. */
        String nameFr,
        String icon,
        String imageUrl,
        String createdByName,
        List<SubcategoryResponse> subcategories,
        long productCount
) {
}
