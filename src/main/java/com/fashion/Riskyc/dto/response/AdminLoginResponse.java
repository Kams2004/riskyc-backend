package com.fashion.Riskyc.dto.response;

import com.fashion.Riskyc.entity.Permission;

import java.util.Set;
import java.util.UUID;

public record AdminLoginResponse(
        String token,
        AdminUserResponse user,
        Set<Permission> permissions,
        /** True right after invite-provisioning — the frontend must route straight to a "set your new password" screen and block everything else until it's cleared. */
        boolean mustChangePassword,
        /** The top-level Category ids this admin's enterprise is approved to list products under. Empty for platform staff (who aren't restricted to any subset). */
        Set<UUID> enterpriseCategoryIds
) {
}
