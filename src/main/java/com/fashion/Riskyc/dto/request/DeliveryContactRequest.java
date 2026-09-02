package com.fashion.Riskyc.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DeliveryContactRequest(
        @NotBlank String name,
        @NotBlank String phone,
        Integer position
) {
}
