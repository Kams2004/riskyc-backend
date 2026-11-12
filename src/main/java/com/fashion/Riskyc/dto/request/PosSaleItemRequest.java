package com.fashion.Riskyc.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One line of a till sale — either:
 * <ul>
 *   <li>a <b>product line</b> (productId set): priced and stock-counted exactly as before. selectedColor/selectedSize
 *       default to "" for a product with no color/size options, matching ProductVariant's own convention; or</li>
 *   <li>an <b>amount line</b> (amount set, no productId): the till's calculator mode for a busy shop — a typed amount
 *       recorded as "Articles divers" (or {@code label}), {@code quantity} times, with no product, no stock and no
 *       bulk pricing.</li>
 * </ul>
 * Exactly one of productId / amount must be set; PosSaleService rejects anything else with 400.
 */
public record PosSaleItemRequest(
        UUID productId,
        @Positive Integer quantity,
        String selectedColor,
        String selectedSize,
        @Positive BigDecimal amount,
        @Size(max = 120) String label
) {
    /** A product line — the shape every client sent before amount lines existed. */
    public PosSaleItemRequest(UUID productId, Integer quantity, String selectedColor, String selectedSize) {
        this(productId, quantity, selectedColor, selectedSize, null, null);
    }

    public boolean isAmountLine() {
        return productId == null && amount != null;
    }

    /** Quantity as sent; an amount line may leave it out (= 1). */
    public int effectiveQuantity() {
        return quantity != null ? quantity : 1;
    }

    public String colorOrEmpty() {
        return selectedColor != null ? selectedColor : "";
    }

    public String sizeOrEmpty() {
        return selectedSize != null ? selectedSize : "";
    }
}
