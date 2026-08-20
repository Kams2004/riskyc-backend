package com.fashion.Riskyc.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ProductColorRequest(
        @NotBlank String name,
        @NotBlank String hex,
        Integer stock
) {
}
