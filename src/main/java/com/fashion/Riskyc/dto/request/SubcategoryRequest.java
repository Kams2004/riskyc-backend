package com.fashion.Riskyc.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SubcategoryRequest(
        @NotBlank String slug,
        @NotBlank String name
) {
}
