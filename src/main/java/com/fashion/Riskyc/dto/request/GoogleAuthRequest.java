package com.fashion.Riskyc.dto.request;

import jakarta.validation.constraints.NotBlank;

public record GoogleAuthRequest(
        @NotBlank String idToken,
        /** Optional — only applied the first time this Google account creates a customer. */
        String referralCode
) {
}
