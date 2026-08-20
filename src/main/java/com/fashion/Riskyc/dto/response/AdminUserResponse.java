package com.fashion.Riskyc.dto.response;

import com.fashion.Riskyc.entity.AccountStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        UUID roleId,
        String roleName,
        AccountStatus status,
        Instant createdAt,
        Instant lastLogin
) {
}
