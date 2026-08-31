package com.fashion.Riskyc.dto.response;

import com.fashion.Riskyc.entity.CustomerStatus;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        CustomerStatus status,
        Instant createdAt,
        Instant lastLogin,
        String referralCode,
        String acronym,
        /** Acronym of whoever referred this customer in, if anyone. */
        String referredByAcronym
) {
}
