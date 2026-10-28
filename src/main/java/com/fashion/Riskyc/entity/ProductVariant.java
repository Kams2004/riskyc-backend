package com.fashion.Riskyc.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * The real stock ledger — one row per sellable (product, color, size)
 * combination, shared by online checkout and POS. Replaces
 * {@link ProductColor#getStock()} as the number that actually gets
 * checked/decremented; that field is left in place (deprecated, not
 * removed) during the transition rather than repurposed.
 *
 * <p>Keyed by plain strings, not a {@code ProductColor} FK, to match the
 * exact convention {@code OrderItem.selectedColor}/{@code selectedSize}
 * already use: an empty string means "this product has no color/size
 * options", not null. A product with neither colors nor sizes still gets
 * exactly one variant row, colorName="" size="".
 */
@Entity
@Table(name = "product_variants", uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "color_name", "size"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnore
    private Product product;

    @Column(name = "color_name", nullable = false)
    @Builder.Default
    private String colorName = "";

    @Column(nullable = false)
    @Builder.Default
    private String size = "";

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 0;

    /** Optional — drives a future "low stock" admin badge. Not surfaced to the storefront. */
    private Integer lowStockThreshold;

    /** Seeded by the migration backfill for a product whose old ProductColor.stock was null ("untracked") — lets admins distinguish "really has 999999 units" from "nobody has set a real number yet" without a schema change. */
    @Column(nullable = false)
    @Builder.Default
    private boolean untracked = false;

    /** Belt-and-suspenders alongside the pessimistic lock used at decrement time. */
    @Version
    private Long version;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
