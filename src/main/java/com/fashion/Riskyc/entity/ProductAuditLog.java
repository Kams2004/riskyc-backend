package com.fashion.Riskyc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per section changed on a product — who, what, when. Deliberately
 * its own table rather than columns on {@link Product} or cascaded from it:
 * a deleted product's history should stay readable (same reasoning as
 * {@code OrderItem} snapshotting a product's name/price rather than losing
 * them when the product is later removed), so {@code productId} is a plain
 * UUID, not a foreign key, and {@code productName} is a snapshot.
 */
@Entity
@Table(name = "product_audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private String productName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductAuditSection section;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    /** Name snapshot, not a live FK — survives the admin account being renamed or removed later. */
    private String changedByName;

    private UUID changedById;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant changedAt;
}
