package com.fashion.Riskyc.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Null for a guest checkout — the order still carries {@link #customerInfo}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    /** Which seller this order's items belong to — a cart spanning several sellers becomes one Order per enterprise, see OrderService#create(). Nullable only transiently, until DataSeeder backfills pre-marketplace orders onto the platform-default enterprise. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enterprise_id")
    private Enterprise enterprise;

    /** Groups the sibling Orders produced by one checkout (one per seller involved) as "the same purchase" — a label, not a foreign key to another table. Null for orders created before this field existed. */
    private UUID checkoutGroupId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonManagedReference
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Embedded
    private CustomerInfo customerInfo;

    /**
     * Snapshotted from whichever app language was active at checkout —
     * mirrors how each push subscription already snapshots its own language
     * at subscribe time. The single source of truth every customer-facing
     * channel (SMS, in-app notifications) resolves against for this order,
     * so they can't drift into different languages for the same event.
     */
    @Column(length = 5)
    @Builder.Default
    private String language = "en";

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    private String paymentCode;

    /** Account-holder name shown alongside the code above (e.g. "KHAN RAPHEAL"). */
    private String paymentAccountName;

    /** MinIO object key of the payment proof screenshot the customer uploaded, if any. */
    private String paymentScreenshotKey;

    // ── Audit trail — who did what, and when. Names are snapshotted (not a
    // live FK to AdminUser) so history reads correctly even if that admin
    // account is later renamed or deleted. ──
    private String statusChangedByName;
    private Instant statusChangedAt;

    /** Set when an admin cancels/rejects the order — shown to the customer on the tracking page. */
    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    private String packagingStartedByName;
    /** Paired with the name snapshot above — the stable id a "was this started by me?" check compares against (names can be edited/reused, ids can't). */
    private UUID packagingStartedById;
    private Instant packagingStartedAt;
    private String packagingCompletedByName;
    private UUID packagingCompletedById;
    private Instant packagingCompletedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
