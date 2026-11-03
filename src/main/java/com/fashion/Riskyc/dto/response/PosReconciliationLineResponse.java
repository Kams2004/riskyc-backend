package com.fashion.Riskyc.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One oversold sale line — a PosSaleItem whose stockBeforeSale was less than
 * the quantity sold — with enough sale context to act on it without a second
 * lookup. stockAfter and oversoldBy are derived, not stored.
 */
public record PosReconciliationLineResponse(
        UUID itemId,
        UUID saleId,
        Instant soldAt,
        String cashierName,
        String productName,
        String thumbnailUrl,
        String selectedColor,
        String selectedSize,
        Integer quantity,
        BigDecimal unitPrice,
        Integer stockBefore,
        Integer stockAfter,
        Integer oversoldBy,
        boolean reconciled,
        Instant reconciledAt,
        String reconciledByName
) {
}
