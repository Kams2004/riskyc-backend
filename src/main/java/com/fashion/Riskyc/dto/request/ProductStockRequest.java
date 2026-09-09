package com.fashion.Riskyc.dto.request;

import jakarta.validation.Valid;

import java.util.List;

/** The UPDATE_PRODUCT_STOCK section — updates only the stock count of existing color entries, never adds/removes/renames one. */
public record ProductStockRequest(
        @Valid List<ProductStockEntryRequest> colors
) {
}
