package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.LocalizedText;
import com.fashion.Riskyc.entity.ExpoPushToken;
import com.fashion.Riskyc.entity.PushSubscription;
import com.fashion.Riskyc.repository.ExpoPushTokenRepository;
import com.fashion.Riskyc.repository.PushSubscriptionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.security.Security;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends order-status push notifications on both channels a customer might be
 * on: browser Web Push (see {@link PushSubscription}) and the mobile app's
 * Expo push tokens (see {@link ExpoPushToken}). Customers have no account,
 * so there's no "notify this user" concept — both are tied to the order id
 * itself, the same model the public order-tracking page/screen already uses.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationService {

    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final ExpoPushTokenRepository expoPushTokenRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

    @Value("${app.push.vapid-public-key}")
    private String vapidPublicKey;

    @Value("${app.push.vapid-private-key}")
    private String vapidPrivateKey;

    @Value("${app.push.vapid-subject}")
    private String vapidSubject;

    private PushService pushService;

    @PostConstruct
    void init() {
        Security.addProvider(new BouncyCastleProvider());
        try {
            pushService = new PushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
        } catch (Exception e) {
            log.error("Failed to initialize Web Push service — push notifications are disabled", e);
        }
    }

    public String vapidPublicKey() {
        return vapidPublicKey;
    }

    /** Fire-and-forget: never lets a bad/expired subscription (or a push service being down) affect the caller. Each subscriber gets the title/body in the language it subscribed with. */
    public void notifyOrder(UUID orderId, LocalizedText title, LocalizedText body, String url) {
        notifyWebPushSubscribers(orderId, title, body, url);
        notifyExpoSubscribers(orderId, title, body, url);
    }

    private void notifyWebPushSubscribers(UUID orderId, LocalizedText title, LocalizedText body, String url) {
        if (pushService == null) return;
        List<PushSubscription> subs = pushSubscriptionRepository.findByOrderId(orderId);
        if (subs.isEmpty()) return;

        for (PushSubscription sub : subs) {
            String payload;
            try {
                payload = objectMapper.writeValueAsString(Map.of(
                        "title", title.forLanguage(sub.getLanguage()),
                        "body", body.forLanguage(sub.getLanguage()),
                        "url", url));
            } catch (Exception e) {
                log.error("Failed to serialize push payload", e);
                continue;
            }
            try {
                Notification notification = new Notification(sub.getEndpoint(), sub.getP256dh(), sub.getAuth(), payload);
                HttpResponse response = pushService.send(notification);
                int status = response.getStatusLine().getStatusCode();
                if (status == 404 || status == 410) {
                    // The browser unsubscribed or the subscription expired — stop trying it.
                    pushSubscriptionRepository.delete(sub);
                } else if (status >= 300) {
                    log.warn("Push send to {} returned status {}", sub.getEndpoint(), status);
                }
            } catch (Exception e) {
                log.warn("Failed to send push notification to a subscriber of order {}: {}", orderId, e.getMessage());
            }
        }
    }

    private void notifyExpoSubscribers(UUID orderId, LocalizedText title, LocalizedText body, String url) {
        List<ExpoPushToken> tokens = expoPushTokenRepository.findByOrderId(orderId);
        if (tokens.isEmpty()) return;

        for (ExpoPushToken token : tokens) {
            try {
                Map<String, Object> message = Map.of(
                        "to", token.getToken(),
                        "title", title.forLanguage(token.getLanguage()),
                        "body", body.forLanguage(token.getLanguage()),
                        "data", Map.of("url", url, "orderId", orderId.toString())
                );
                Map<?, ?> response = restClient.post()
                        .uri(EXPO_PUSH_URL)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .body(message)
                        .retrieve()
                        .body(Map.class);
                Object data = response != null ? response.get("data") : null;
                Object status = data instanceof Map<?, ?> m ? m.get("status") : null;
                Object errDetails = data instanceof Map<?, ?> m ? m.get("details") : null;
                if ("error".equals(status)) {
                    Object errType = errDetails instanceof Map<?, ?> d ? d.get("error") : null;
                    if ("DeviceNotRegistered".equals(errType)) {
                        // The app was uninstalled or the token otherwise expired — stop trying it.
                        expoPushTokenRepository.delete(token);
                    } else {
                        log.warn("Expo push to order {} returned error: {}", orderId, data);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to send Expo push notification for order {}: {}", orderId, e.getMessage());
            }
        }
    }
}
