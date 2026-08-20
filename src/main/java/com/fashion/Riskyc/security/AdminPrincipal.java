package com.fashion.Riskyc.security;

import com.fashion.Riskyc.entity.Permission;

import java.util.Set;

/** The authenticated admin identity extracted from a validated JWT. */
public record AdminPrincipal(
        String userId,
        String email,
        String roleId,
        String roleName,
        Set<Permission> permissions
) {
    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }
}
