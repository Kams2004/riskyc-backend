package com.fashion.Riskyc.service;

import com.fashion.Riskyc.entity.PushSubscription;
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
import tools.jackson.databind.ObjectMapper;

import java.security.Security;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends browser Web Push notifications for order-status changes. Customers
 * have no account, so there's no "notify this user" concept — subscriptions
 * are tied to the order id itself (see {@link PushSubscription}), the same
 * model the public order-tracking page already uses.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationService {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    /** Fire-and-forget: never lets a bad/expired subscription (or the push service being down) affect the caller. */
    public void notifyOrder(UUID orderId, String title, String body, String url) {
        if (pushService == null) return;
        List<PushSubscription> subs = pushSubscriptionRepository.findByOrderId(orderId);
        if (subs.isEmpty()) return;

        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of("title", title, "body", body, "url", url));
        } catch (Exception e) {
            log.error("Failed to serialize push payload", e);
            return;
        }

        for (PushSubscription sub : subs) {
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
}
