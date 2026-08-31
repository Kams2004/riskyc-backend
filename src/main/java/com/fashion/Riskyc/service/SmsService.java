package com.fashion.Riskyc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.nio.charset.StandardCharsets;

/**
 * Sends SMS to customers via Orange's "SMS Cameroon" API — reaches a
 * customer regardless of whether they ever enabled browser push
 * notifications, since a phone number is required at checkout. Silently
 * does nothing when unconfigured (no client id/secret/sender), so the app
 * works identically before and after credentials are added.
 *
 * API contract (Orange Developer — SMS Africa & Middle East / SMS Cameroon),
 * confirmed against Orange's own published integration guide:
 *   token: POST https://api.orange.com/oauth/v3/token   (client_credentials, Basic auth)
 *   send:  POST https://api.orange.com/smsmessaging/v1/outbound/tel%3A%2B{senderNumber}/requests
 * The "tel:+{senderNumber}" segment must be percent-encoded in the URL path
 * itself (colon and plus aren't legal unescaped there) — built as a raw
 * java.net.URI below so Spring doesn't re-encode (or double-encode) it.
 * senderNumber is the fixed per-country value from Orange's own table (e.g.
 * "2370000" for Cameroon) — the same for every account in that country, not
 * something specific to this subscription.
 */
@Service
@Slf4j
public class SmsService {

    private static final String TOKEN_URL = "https://api.orange.com/oauth/v3/token";

    private final RestClient restClient = RestClient.create();

    @Value("${app.orange.sms.client-id:}")
    private String clientId;

    @Value("${app.orange.sms.client-secret:}")
    private String clientSecret;

    /**
     * The fixed per-country sender address Orange documents for their SMS
     * API (digits only, no '+') — "2370000" for Cameroon, same for every
     * account in that country. NOT your own phone number, and NOT the
     * numeric "SMS <n>" default sender *name* shown under Authorized senders.
     */
    @Value("${app.orange.sms.sender-number:}")
    private String senderNumber;

    /** The approved custom sender name (max 11 chars) shown as the "from" on the customer's phone — see the Orange Developer "Request custom Sender Names" form. */
    @Value("${app.orange.sms.sender-name:}")
    private String senderName;

    private volatile String cachedToken;
    private volatile Instant cachedTokenExpiresAt;

    private boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !senderNumber.isBlank();
    }

    /** Best-effort — logs and gives up quietly on any failure rather than breaking the order-status flow that triggered it. */
    public void send(String toPhoneNumber, String message) {
        if (!isConfigured() || toPhoneNumber == null || toPhoneNumber.isBlank()) return;
        try {
            String token = getAccessToken();
            String to = normalizePhone(toPhoneNumber);

            // senderName is optional on Orange's side (falls back to the
            // account's default numeric sender) — omitted entirely rather
            // than sent as an empty string when no custom name is approved yet.
            Map<String, Object> request = new java.util.LinkedHashMap<>();
            request.put("address", "tel:" + to);
            request.put("senderAddress", "tel:+" + senderNumber);
            if (senderName != null && !senderName.isBlank()) {
                request.put("senderName", senderName);
            }
            request.put("outboundSMSTextMessage", Map.of("message", message));
            Map<String, Object> payload = Map.of("outboundSMSMessageRequest", request);

            URI sendUri = URI.create("https://api.orange.com/smsmessaging/v1/outbound/tel%3A%2B" + senderNumber + "/requests");
            restClient.post()
                    .uri(sendUri)
                    .header("Authorization", "Bearer " + token)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Failed to send SMS to {}: {}", toPhoneNumber, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private synchronized String getAccessToken() {
        if (cachedToken != null && cachedTokenExpiresAt != null && Instant.now().isBefore(cachedTokenExpiresAt.minusSeconds(30))) {
            return cachedToken;
        }
        String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri(TOKEN_URL)
                    .header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("grant_type=client_credentials")
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Orange OAuth token request failed: " + e.getMessage(), e);
        }
        if (response == null || response.get("access_token") == null) {
            throw new IllegalStateException("Orange OAuth token response had no access_token");
        }
        cachedToken = String.valueOf(response.get("access_token"));
        int expiresIn = response.get("expires_in") != null ? Integer.parseInt(String.valueOf(response.get("expires_in"))) : 3600;
        cachedTokenExpiresAt = Instant.now().plusSeconds(expiresIn);
        return cachedToken;
    }

    /** Customers type local numbers ("6XX XX XX XX") at checkout — normalize to +237XXXXXXXXX for the API. */
    private String normalizePhone(String raw) {
        String digits = raw.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+")) return digits;
        if (digits.startsWith("237")) return "+" + digits;
        return "+237" + digits;
    }
}
