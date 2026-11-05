package com.fashion.Riskyc.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.fashion.Riskyc.entity.CustomerStatus;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
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
        String referredByAcronym,
        /** Only on login/register/Google sign-in: the customer's session token, sent back as "Authorization: Bearer". */
        String token
) {
    public CustomerResponse withToken(String sessionToken) {
        return new CustomerResponse(id, firstName, lastName, email, phone, status, createdAt, lastLogin,
                referralCode, acronym, referredByAcronym, sessionToken);
    }
}
