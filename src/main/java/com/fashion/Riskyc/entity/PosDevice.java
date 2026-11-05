package com.fashion.Riskyc.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A physical till (the POS desktop app on one shop PC). It authenticates with its own long-lived token — not a
 * cashier's login — so it can keep checking in and upload sales queued offline even after every cashier's
 * session has expired. Bound to one seller (enterprise null = the platform's own till) and revocable.
 */
@Entity
@Table(name = "pos_devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enterprise_id")
    private Enterprise enterprise;

    @Column(nullable = false)
    private String name;

    /** e.g. "windows" / "linux", as reported by the app at enrolment. */
    private String platform;

    /** SHA-256 (hex) of the device secret; the secret itself is only ever shown to the device once, at enrolment. */
    @Column(nullable = false, length = 64)
    private String tokenHash;

    private String enrolledByName;

    @Column(nullable = false)
    private Instant enrolledAt;

    private Instant lastSeenAt;

    private String appVersion;

    /** Sales waiting in the device's offline queue, as of its last check-in. */
    @Column(nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private int pendingSales = 0;

    private Instant revokedAt;

    private String revokedByName;
}
