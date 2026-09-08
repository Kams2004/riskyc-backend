package com.fashion.Riskyc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Mirrors the shape of the browser's native `PushSubscription.toJSON()`. */
public record PushSubscribeRequest(
        @NotNull UUID orderId,
        @NotBlank String endpoint,
        @NotNull Keys keys,
        /** "en" or "fr" — the app's active language when it subscribed. Optional; defaults to "en". */
        String language
) {
    public record Keys(@NotBlank String p256dh, @NotBlank String auth) {
    }
}
