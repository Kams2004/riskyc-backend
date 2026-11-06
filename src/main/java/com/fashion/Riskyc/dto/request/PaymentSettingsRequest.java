package com.fashion.Riskyc.dto.request;

import jakarta.validation.constraints.Size;

/** A shop's own payment numbers. Blank clears a method; a code must contain "{amount}" (or "%s") where the amount goes. */
public record PaymentSettingsRequest(
        @Size(max = 120) String orangeMoneyCode,
        @Size(max = 120) String orangeMoneyName,
        @Size(max = 120) String mobileMoneyCode,
        @Size(max = 120) String mobileMoneyName
) {
}
