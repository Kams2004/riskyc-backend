package com.fashion.Riskyc.dto;

/** A push notification's title/body in both languages the app supports — picked per-subscriber by {@link com.fashion.Riskyc.service.PushNotificationService}. */
public record LocalizedText(String en, String fr) {
    public String forLanguage(String language) {
        return "fr".equalsIgnoreCase(language) ? fr : en;
    }
}
