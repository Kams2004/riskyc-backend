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

    /**
     * The app language active on this device when it subscribed ("en"/"fr")
     * — lets a push notification's text match what the customer reads
     * everywhere else. Nullable at the DB level (every write path always
     * sets it, so it's never actually null going forward) — a NOT NULL
     * column added later via ddl-auto=update has no default clause Postgres
     * can use to backfill existing rows, so it fails silently on a table
     * that already has any; LocalizedText#forLanguage already treats a null
     * language the same as "en".
     */
    @Builder.Default
    @Column(length = 5)
    private String language = "en";

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
