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

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** Optional — an admin can publish a product without writing a description. */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Machine-translated French version of {@link #name}, regenerated whenever name changes — see TranslationService. Null until a translation call has succeeded at least once. */
    private String nameFr;

    /** Machine-translated French version of {@link #description}. */
    @Column(columnDefinition = "TEXT")
    private String descriptionFr;

    /** Optional — an admin can publish a product before deciding a price; defaults to zero ("price on request"). */
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    /** Struck-through "was" price used to compute a discount badge. */
    @Column(precision = 12, scale = 2)
    private BigDecimal originalPrice;

    /** Optional "buy N for this total" tiers — unrelated to the regular unit price above. */
    @ElementCollection
    @CollectionTable(name = "product_bulk_prices", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "position")
    @Builder.Default
    private List<BulkPriceTier> bulkPrices = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subcategory_id")
    private Subcategory subcategory;

    @ElementCollection
    @CollectionTable(name = "product_sizes", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "size")
    @OrderColumn(name = "position")
    @Builder.Default
    private List<String> sizes = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "product_tags", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "tag")
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @Column(nullable = false)
    @Builder.Default
    private Double rating = 4.5;

    @Column(nullable = false)
    @Builder.Default
    private Integer reviews = 0;

    @Enumerated(EnumType.STRING)
    private Badge badge;

    /** Hidden products are excluded from every storefront-facing query. */
    @Column(nullable = false)
    @Builder.Default
    private boolean hidden = false;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductColor> colors = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("uploadedAt ASC")
    @Builder.Default
    private List<ProductMedia> media = new ArrayList<>();

    /** Which admin created this product — a name snapshot, not a live FK. */
    private String createdByName;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
