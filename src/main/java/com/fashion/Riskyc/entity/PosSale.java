package com.fashion.Riskyc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One in-person sale rung up at a physical till — the POS analogue of
 * {@link Order}, with the same "snapshot the product name/price, keep a
 * nullable product FK" convention as {@link OrderItem}.
 *
 * <p>{@code clientSaleId} is the idempotency key: it's generated on the POS
 * device the moment the sale is rung up (online or not), and a sync retry
 * after a partial failure must not create a second row for the same sale —
 * see PosSaleService#create.
 */
@Entity
@Table(name = "pos_sales")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosSale {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID clientSaleId;

    private UUID cashierId;

    @Column(nullable = false)
    private String cashierName;

    /** Which seller this till sale's items belong to — null only for a platform-rung sale whose items span more than one enterprise (only possible for an unscoped admin; a scoped admin's sale is always restricted to their own enterprise, see PosSaleService#create), or for a pre-marketplace sale DataSeeder hasn't backfilled yet. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enterprise_id")
    private Enterprise enterprise;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PosSaleItem> items = new ArrayList<>();

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PosPaymentMethod paymentMethod;

    /** Digital receipt, uploaded after the fact — same S3MediaService key-only pattern as Order.paymentScreenshotKey. */
    private String receiptKey;

    /** When the sale actually happened on the till — distinct from createdAt, which is when this row was persisted (possibly hours later, for an offline sale synced on reconnect). */
    @Column(nullable = false)
    private Instant soldAt;

    /** True if decrementing stock for this sale pushed any variant negative — a signal for admin reconciliation, never a reason to reject the sale itself (it already happened). */
    @Column(nullable = false)
    @Builder.Default
    private boolean stockReconciliationFlag = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean voided = false;

    private String voidedByName;

    private Instant voidedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
