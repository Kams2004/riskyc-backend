package com.fashion.Riskyc.dto.response;

import java.util.UUID;

public record DeliveryContactResponse(
        UUID id,
        String name,
        String phone,
        int position
) {
}
