package com.fashion.Riskyc.dto.request;

import com.fashion.Riskyc.entity.PosPaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * clientSaleId is generated on the POS device the moment the sale is rung
 * up — the idempotency key for a retried sync (see PosSale's javadoc). The
 * cashier is deliberately NOT part of this request: it's always the
 * authenticated caller (CurrentAdmin), never client-supplied.
 */
public record CreatePosSaleRequest(
        @NotNull UUID clientSaleId,
        @NotEmpty @Valid List<PosSaleItemRequest> items,
        @NotNull PosPaymentMethod paymentMethod,
        /** When the sale actually happened on the till — may be well before this request reaches the server, if rung up offline. */
        @NotNull Instant soldAt
) {
}
