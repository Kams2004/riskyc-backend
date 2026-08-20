package com.fashion.Riskyc.dto.request;

import com.fashion.Riskyc.entity.AccountStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AdminUserRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Email String email,
        /** Optional on update — leave blank to keep the current password. */
        String password,
        @NotNull UUID roleId,
        AccountStatus status
) {
}
