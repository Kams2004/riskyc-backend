package com.fashion.Riskyc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One slide in the promotional carousel shown on the mobile home screen —
 * an image or short video, with an optional overlay caption. Same
 * "only the MinIO object key is persisted" pattern as {@link ProductMedia}.
 */
@Entity
@Table(name = "publicity_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicityItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Object key inside the MinIO bucket, e.g. {@code publicity/<uuid>.jpg}. */
    @Column(nullable = false, unique = true)
    private String storageKey;

    private String originalFilename;

    private String contentType;

    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaType type;

    /** Optional text overlaid on the slide. */
    @Column(columnDefinition = "TEXT")
    private String caption;

    /**
     * Which of the app's two brand fonts the caption is rendered in: "sans" (Inter) or
     * "display" (Playfair Display). Nullable at the DB level on purpose — these two
     * columns were added after publicity_items already had rows in production, and
     * Postgres rejects "ADD COLUMN ... NOT NULL" with no default against a non-empty
     * table. {@link PublicityService#toResponse} coalesces a null to the same default
     * applied here for anything created through the builder.
     */
    @Builder.Default
    private String captionFont = "sans";

    /** "normal" | "bold" | "italic" | "boldItalic". Nullable at the DB level — see {@link #captionFont}. */
    @Builder.Default
    private String captionStyle = "bold";

    /** Display order within the carousel — lower first. Unlike ProductMedia, this needs to be explicitly admin-controlled (reorderable), not just upload order. */
    @Column(nullable = false)
    @Builder.Default
    private Integer position = 0;

    /** Lets an admin temporarily pull a slide from the customer-facing carousel without deleting it. */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant uploadedAt;
}
