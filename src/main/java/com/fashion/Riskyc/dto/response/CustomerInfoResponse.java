package com.fashion.Riskyc.dto.response;

import com.fashion.Riskyc.entity.DeliveryType;

public record CustomerInfoResponse(
        String firstName,
        String lastName,
        String phone,
        String town,
        String street,
        DeliveryType deliveryType
) {
}
