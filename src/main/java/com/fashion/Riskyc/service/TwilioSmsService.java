package com.fashion.Riskyc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Sends SMS to customers via Twilio's REST API — an alternative to
 * {@link SmsService} (Orange), preferred over it when configured (see
 * OrderService — only one provider is used per message, never both, so
 * configuring this doesn't double the SMS cost of an order).
 *
 * API contract (Twilio, stable since 2010, confirmed against their current
 * docs): POST https://api.twilio.com/2010-04-01/Accounts/{accountSid}/Messages.json
 * (Basic auth: accountSid:authToken, form-urlencoded body: To/From/Body).
 *
 * IMPORTANT — Cameroon-specific (per Twilio's own SMS guidelines for CM):
 * a plain numeric "From" (an ordinary Twilio phone number) is REJECTED
 * outright for MTN Cameroon recipients ("Numeric International sender ID
 * is not supported to the MTN network in Cameroon"). Only a pre-registered
 * Alphanumeric Sender ID reaches MTN numbers — registration takes about 3
 * weeks. A numeric Twilio number works for Orange Cameroon recipients in
 * the meantime. Also: Twilio TRIAL accounts can only send to phone numbers
 * manually verified under Console > Phone Numbers > Verified Caller IDs —
 * add a payment method to lift that restriction.
 */
@Service
@Slf4j
public class TwilioSmsService {

    private static final String MESSAGES_URL_TEMPLATE = "https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json";

    private final RestClient restClient = RestClient.create();

    @Value("${app.twilio.account-sid:}")
    private String accountSid;

    @Value("${app.twilio.auth-token:}")
    private String authToken;

    /** Either a Twilio phone number in E.164 (e.g. "+15017122661") or an approved Alphanumeric Sender ID (e.g. "RiskycFash") — see class javadoc for which recipients each can actually reach in Cameroon. */
    @Value("${app.twilio.from:}")
    private String from;

    public boolean isConfigured() {
        return !accountSid.isBlank() && !authToken.isBlank() && !from.isBlank();
    }

    /** Best-effort — logs and gives up quietly on any failure rather than breaking the order-status flow that triggered it. */
    public void send(String toPhoneNumber, String message) {
        if (!isConfigured() || toPhoneNumber == null || toPhoneNumber.isBlank()) return;
        try {
            String to = normalizePhone(toPhoneNumber);
            String basic = Base64.getEncoder().encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));

            String form = "To=" + URLEncoder.encode(to, StandardCharsets.UTF_8)
                    + "&From=" + URLEncoder.encode(from, StandardCharsets.UTF_8)
                    + "&Body=" + URLEncoder.encode(message, StandardCharsets.UTF_8);

            restClient.post()
                    .uri(String.format(MESSAGES_URL_TEMPLATE, accountSid))
                    .header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Failed to send SMS via Twilio to {}: {}", toPhoneNumber, e.getMessage());
        }
    }

    /** Customers type local numbers ("6XX XX XX XX") at checkout — normalize to +237XXXXXXXXX for the API. */
    private String normalizePhone(String raw) {
        String digits = raw.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+")) return digits;
        if (digits.startsWith("237")) return "+" + digits;
        return "+237" + digits;
    }
}
