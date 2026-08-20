package com.fashion.Riskyc.dto.request;

import com.fashion.Riskyc.entity.Permission;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public record RoleRequest(
        @NotBlank String name,
        String description,
        Set<Permission> permissions
) {
}
