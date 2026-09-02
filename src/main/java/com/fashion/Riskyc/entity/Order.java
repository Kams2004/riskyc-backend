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
