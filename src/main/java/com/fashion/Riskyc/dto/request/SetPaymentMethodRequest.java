package com.fashion.Riskyc.dto.request;

import com.fashion.Riskyc.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record SetPaymentMethodRequest(
        @NotNull PaymentMethod paymentMethod
) {
}
