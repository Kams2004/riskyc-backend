package com.fashion.Riskyc.dto.response;

import java.util.UUID;

public record ProductColorResponse(
        UUID id,
        String name,
        String hex,
        Integer stock
) {
}
