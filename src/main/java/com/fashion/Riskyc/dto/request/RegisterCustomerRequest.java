package com.fashion.Riskyc.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterCustomerRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Email String email,
        String phone,
        @NotBlank @Size(min = 6, message = "Password must be at least 6 characters") String password,
        /** Optional — another customer's referral code, entered by hand or prefilled from a shared link. */
        String referralCode
) {
}
