package com.fashion.Riskyc.controller;

import com.fashion.Riskyc.dto.request.PaymentSettingsRequest;
import com.fashion.Riskyc.dto.response.PaymentSettingsResponse;
import com.fashion.Riskyc.service.PaymentSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** The signed-in admin's own shop's payment numbers (Riskyc's store for platform staff) — MANAGE_PAYMENT_SETTINGS. */
@RestController
@RequestMapping("/api/enterprises/me/payment-settings")
@RequiredArgsConstructor
public class PaymentSettingsController {

    private final PaymentSettingsService paymentSettingsService;

    @GetMapping
    public PaymentSettingsResponse get() {
        return paymentSettingsService.get();
    }

    @PutMapping
    public PaymentSettingsResponse update(@Valid @RequestBody PaymentSettingsRequest request) {
        return paymentSettingsService.update(request);
    }
}
