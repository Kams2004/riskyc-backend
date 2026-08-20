package com.fashion.Riskyc.dto.response;

import java.util.UUID;

public record SubcategoryResponse(
        UUID id,
        String slug,
        String name
) {
}
