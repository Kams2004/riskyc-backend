package com.fashion.Riskyc.dto.request;

import jakarta.validation.Valid;

import java.util.List;

/** The UPDATE_PRODUCT_COLORS section — add/remove/rename color entries. Each request's stock field, if present, is ignored (see UPDATE_PRODUCT_STOCK). */
public record ProductColorsRequest(
        @Valid List<ProductColorRequest> colors
) {
}
