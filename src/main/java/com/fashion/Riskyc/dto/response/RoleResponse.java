package com.fashion.Riskyc.dto.response;

import com.fashion.Riskyc.entity.Permission;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String name,
        String description,
        Set<Permission> permissions,
        Instant createdAt
) {
}
