package com.fashion.Riskyc.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * colorName/size default to "" client-side for a product with no color/size options — never null.
 * {@code untracked = true} makes this variant unlimited again (quantity is then ignored); omitted or false means
 * "this is the real count", which is how every older client calls it.
 */
public record ProductVariantStockEntryRequest(
        @NotNull String colorName,
        @NotNull String size,
        @NotNull Integer quantity,
        Boolean untracked
) {
    public boolean unlimited() {
        return Boolean.TRUE.equals(untracked);
    }
}
