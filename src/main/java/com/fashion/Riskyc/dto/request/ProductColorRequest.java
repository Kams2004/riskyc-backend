package com.fashion.Riskyc.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/** {@code id} is null for a brand-new color entry, or an existing color's id to update it in place (preserving its stock) instead of recreating it. */
public record ProductColorRequest(
        UUID id,
        @NotBlank String name,
        @NotBlank String hex,
        Integer stock
) {
}
