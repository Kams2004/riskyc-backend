package com.fashion.Riskyc.dto.request;

import com.fashion.Riskyc.entity.PosPaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One sale from a till's offline queue — same shape as CreatePosSaleRequest, plus who rang it up. */
public record DeviceSaleRequest(
        @NotNull UUID clientSaleId,
        @NotNull UUID cashierId,
        @NotEmpty @Valid List<PosSaleItemRequest> items,
        @NotNull PosPaymentMethod paymentMethod,
        @NotNull Instant soldAt
) {
    public CreatePosSaleRequest toCreateRequest() {
        return new CreatePosSaleRequest(clientSaleId, items, paymentMethod, soldAt);
    }
}
