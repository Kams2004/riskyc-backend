package com.fashion.Riskyc.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** The UPDATE_PRODUCT_STOCK section, now against the real per-(color,size) ledger — upserts each entry, never removes a variant not mentioned. */
public record ProductVariantStockRequest(
        @NotEmpty @Valid List<ProductVariantStockEntryRequest> variants
) {
}
