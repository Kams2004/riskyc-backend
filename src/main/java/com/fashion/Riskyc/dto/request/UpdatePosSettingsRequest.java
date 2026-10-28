package com.fashion.Riskyc.dto.request;

import com.fashion.Riskyc.entity.ReceiptPaperWidth;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Every field required — this is a full-replace PATCH (the settings form always submits the whole thing), not a section-scoped partial update like Product's. */
public record UpdatePosSettingsRequest(
        @NotBlank String storeName,
        String storeAddress,
        String storePhone,
        String footerMessage,
        @NotNull Boolean showCashierName,
        @NotNull Boolean showSaleId,
        @NotNull ReceiptPaperWidth paperWidth
) {
}
