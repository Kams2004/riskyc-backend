package com.fashion.Riskyc.controller;

import com.fashion.Riskyc.dto.request.PushSubscribeRequest;
import com.fashion.Riskyc.entity.PushSubscription;
import com.fashion.Riskyc.repository.PushSubscriptionRepository;
import com.fashion.Riskyc.service.PushNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public — customers have no account, so a subscription is authorized
 * simply by knowing the order id, the same trust model as the public
 * GET /api/orders/{id} tracking endpoint.
 */
@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    private final PushNotificationService pushNotificationService;
    private final PushSubscriptionRepository pushSubscriptionRepository;

    @GetMapping("/vapid-public-key")
    public Map<String, String> vapidPublicKey() {
        return Map.of("publicKey", pushNotificationService.vapidPublicKey());
    }

    @PostMapping("/subscribe")
    public ResponseEntity<Void> subscribe(@Valid @RequestBody PushSubscribeRequest request) {
        PushSubscription sub = pushSubscriptionRepository.findByEndpoint(request.endpoint())
                .orElseGet(PushSubscription::new);
        sub.setOrderId(request.orderId());
        sub.setEndpoint(request.endpoint());
        sub.setP256dh(request.keys().p256dh());
        sub.setAuth(request.keys().auth());
        pushSubscriptionRepository.save(sub);
        return ResponseEntity.status(201).build();
    }

    @DeleteMapping("/subscribe")
    public ResponseEntity<Void> unsubscribe(@RequestParam String endpoint) {
        pushSubscriptionRepository.deleteByEndpoint(endpoint);
        return ResponseEntity.noContent().build();
    }
}
