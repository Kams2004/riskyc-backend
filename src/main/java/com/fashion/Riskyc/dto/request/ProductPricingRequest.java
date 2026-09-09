package com.fashion.Riskyc.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

/** Price, original price, bulk-price tiers — the UPDATE_PRODUCT_PRICING section of the edit form. */
public record ProductPricingRequest(
        @PositiveOrZero BigDecimal price,
        BigDecimal originalPrice,
        @Valid List<BulkPriceTierRequest> bulkPrices
) {
}
