package com.fashion.Riskyc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SubcategoryRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "must be lowercase letters, numbers and hyphens only")
        String slug,
        @NotBlank String name
) {
}
