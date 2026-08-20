package com.fashion.Riskyc.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CategoryRequest(
        @NotBlank String slug,
        @NotBlank String name,
        String icon
) {
}
