package com.fashion.Riskyc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One till configuration per seller (enterprise), plus one with no enterprise for the platform's own till —
 * so a seller's cashier can only ever change their own shop's tickets. PosSettingsService lazily creates a
 * row with defaults on first read rather than relying on a migration/seed.
 */
@Entity
@Table(name = "pos_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Null for the platform's own till. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enterprise_id", unique = true)
    private Enterprise enterprise;

    @Column(nullable = false)
    @Builder.Default
    private String storeName = "Riskyc Fashion";

    private String storeAddress;

    private String storePhone;

    /** Printed at the bottom of every ticket — e.g. a thank-you line or return policy. */
    @Column(columnDefinition = "TEXT")
    private String footerMessage;

    @Column(nullable = false)
    @Builder.Default
    private boolean showCashierName = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean showSaleId = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReceiptPaperWidth paperWidth = ReceiptPaperWidth.MM80;

    /** S3MediaService storage key, same key-only pattern as every other media field — resolved to a presigned URL at response time. */
    private String logoKey;

    /** Lets a store keep a logo uploaded but skip it on the printed ticket, without having to delete and re-upload it later. */
    // The DB default lets ddl-auto add this NOT NULL column to the existing settings row.
    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private boolean showLogo = true;

    // Which payment buttons the till offers — a shop that doesn't take Orange
    // Money in person, say, turns it off here rather than cashiers just being
    // told not to tap it. All three default on so existing/fresh installs
    // behave exactly as before this field existed.
    @Column(nullable = false)
    @Builder.Default
    private boolean cashEnabled = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean orangeMoneyEnabled = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean mobileMoneyEnabled = true;

    @UpdateTimestamp
    private Instant updatedAt;
}
