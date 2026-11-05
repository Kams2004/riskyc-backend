package com.fashion.Riskyc.dto.response;

import com.fashion.Riskyc.entity.ReceiptPaperWidth;

public record PosSettingsResponse(
        String storeName,
        String storeAddress,
        String storePhone,
        String footerMessage,
        boolean showCashierName,
        boolean showSaleId,
        boolean showLogo,
        ReceiptPaperWidth paperWidth,
        String logoUrl,
        boolean cashEnabled,
        boolean orangeMoneyEnabled,
        boolean mobileMoneyEnabled
) {
}
