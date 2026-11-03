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
        Instant createdAt,
        /** Null = a platform-wide role. Set = scoped to that enterprise's own staff. */
        UUID enterpriseId
) {
}
