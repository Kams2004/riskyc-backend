package com.fashion.Riskyc.dto.response;

import java.util.UUID;

public record SubcategoryResponse(
        UUID id,
        String slug,
        String name,
        /** Machine-translated French version — null until a translation has succeeded. */
        String nameFr
) {
}
