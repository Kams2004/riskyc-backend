package com.fashion.Riskyc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted record of a real-time event pushed over WebSocket (new order,
 * new chat message, payment proof uploaded, order status change) so the
 * bell icon still has history for anyone who wasn't online when it fired.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationRecipient recipientType;

    /** Set only when {@link #recipientType} is {@code CUSTOMER}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String message;

    /** Id of the order/conversation/etc. this notification is about, for deep-linking. */
    private String referenceId;

    @Column(nullable = false)
    @Builder.Default
    private boolean read = false;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
