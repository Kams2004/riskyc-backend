package com.fashion.Riskyc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A single-row table — there's one till configuration, not one per admin.
 * PosSettingsService#get() lazily creates this row with defaults on first
 * read rather than relying on a migration/seed to have done it, so it never
 * needs special backfill handling.
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
