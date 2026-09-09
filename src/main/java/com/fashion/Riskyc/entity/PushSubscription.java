package com.fashion.Riskyc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A browser's Web Push subscription for one specific order — customers have
 * no account, so subscriptions are keyed directly by order id (the same
 * "if you have the link, you can see it" model as the public order-tracking
 * endpoint) rather than by a customer identity.
 */
@Entity
@Table(name = "push_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false, unique = true, length = 512)
    private String endpoint;

    @Column(nullable = false)
    private String p256dh;

    @Column(nullable = false)
    private String auth;

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
