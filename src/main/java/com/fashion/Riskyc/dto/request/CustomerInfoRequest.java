package com.fashion.Riskyc.dto.request;

import com.fashion.Riskyc.entity.DeliveryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CustomerInfoRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String phone,
        String town,
        String street,
        @NotNull DeliveryType deliveryType
) {
}
