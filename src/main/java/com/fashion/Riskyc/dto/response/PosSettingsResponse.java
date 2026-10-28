package com.fashion.Riskyc.dto.response;

import com.fashion.Riskyc.entity.ReceiptPaperWidth;

public record PosSettingsResponse(
        String storeName,
        String storeAddress,
        String storePhone,
        String footerMessage,
        boolean showCashierName,
        boolean showSaleId,
        ReceiptPaperWidth paperWidth,
        String logoUrl
) {
}
