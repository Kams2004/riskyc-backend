package com.fashion.Riskyc.dto.response;

/** A shop's payment numbers as its admins see them; acceptsPayments = at least one method fully set up. */
public record PaymentSettingsResponse(
        String storeName,
        String orangeMoneyCode,
        String orangeMoneyName,
        String mobileMoneyCode,
        String mobileMoneyName,
        boolean acceptsPayments
) {
}
