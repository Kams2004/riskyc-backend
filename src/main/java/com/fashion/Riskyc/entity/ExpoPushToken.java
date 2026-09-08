package com.fashion.Riskyc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A mobile device's Expo push token for one specific order — mirrors
 * {@link PushSubscription} (the browser Web Push equivalent) exactly:
 * customers have no account, so a token is authorized simply by knowing the
 * order id, the same trust model as the public GET /api/orders/{id}
 * tracking endpoint.
 */
@Entity
@Table(name = "expo_push_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpoPushToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false, unique = true, length = 255)
    private String token;

    /** The app language active on this device when it subscribed ("en"/"fr") — lets a push notification's text match what the customer reads everywhere else. */
    @Builder.Default
    @Column(nullable = false, length = 5)
    private String language = "en";

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
