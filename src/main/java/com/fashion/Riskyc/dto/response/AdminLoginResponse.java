package com.fashion.Riskyc.dto.response;

import com.fashion.Riskyc.entity.Permission;

import java.util.Set;

public record AdminLoginResponse(
        String token,
        AdminUserResponse user,
        Set<Permission> permissions
) {
}
