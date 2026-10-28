package com.fashion.Riskyc.dto.response;

import com.fashion.Riskyc.entity.PosPaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PosSaleResponse(
        UUID id,
        UUID clientSaleId,
        UUID cashierId,
        String cashierName,
        List<PosSaleItemResponse> items,
        BigDecimal total,
        PosPaymentMethod paymentMethod,
        String receiptUrl,
        Instant soldAt,
        Instant syncedAt,
        boolean stockReconciliationFlag,
        boolean voided,
        String voidedByName,
        Instant voidedAt
) {
}
