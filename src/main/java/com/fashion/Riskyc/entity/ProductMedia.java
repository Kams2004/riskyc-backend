package com.fashion.Riskyc.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A single uploaded image/video for a product. Only the MinIO object key is
 * persisted — the presigned download URL is resolved on read (and cached)
 * by {@code S3MediaService}, never stored, so it can't go stale in the DB.
 */
@Entity
@Table(name = "product_media")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Object key inside the MinIO bucket, e.g. {@code products/<productId>/<uuid>.jpg}. */
    @Column(nullable = false, unique = true)
    private String storageKey;

    private String originalFilename;

    private String contentType;

    private Long sizeBytes;

    /** Free-text promo caption an admin stamps on this specific image (e.g. "1500frs — 10 for 10,000frs"). */
    @Column(length = 80)
    private String promoLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnore
    private Product product;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant uploadedAt;
}
