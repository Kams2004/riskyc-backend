package com.fashion.Riskyc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * An independent seller on the marketplace. {@link #isPlatformDefault} marks
 * the one row every pre-existing product was backfilled onto when this
 * entity was introduced (the "Riskyc Fashion" catalog itself becomes an
 * Enterprise too, rather than a special-cased null) — see
 * DataSeeder#seedPlatformDefaultEnterprise().
 */
@Entity
@Table(name = "enterprises")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Enterprise {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Free-text business type the applicant gave, e.g. "Fashion retailer" — used as one input to the suggested-category heuristic, not itself a category. */
    private String businessType;

    // Explicit columnDefinition, not just @Enumerated(STRING) alone — without
    // it, Hibernate infers the column type from the enum's *current*
    // constants and bakes a Postgres CHECK constraint listing them at
    // table-creation time, which ddl-auto=update never revisits afterward.
    // A plain varchar column carries no such constraint. See Role.permissions
    // for the exact precedent (this already broke the app once).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(16)")
    @Builder.Default
    private EnterpriseStatus status = EnterpriseStatus.PENDING;

    /** The applicant's own name — becomes the first AdminUser's firstName/lastName at approval time (see EnterpriseService#approve()). */
    private String contactFirstName;

    private String contactLastName;

    @Column(nullable = false)
    private String contactEmail;

    private String contactPhone;

    /** Shown to customers on this seller's products as a "Contact Supplier" WhatsApp link — distinct from {@link #contactPhone}, which is the applicant's own reachability detail for platform admins and isn't otherwise customer-facing. Digits only (country code included), no leading "+" — kept exactly as entered rather than normalized, since wa.me accepts either form. */
    private String whatsappNumber;

    /** Questionnaire answer: what the applicant says they sell, in their own words. */
    @Column(columnDefinition = "TEXT")
    private String whatDoYouSell;

    /** Questionnaire answer: a rough self-reported catalog size, e.g. "1-10", "10-50", "50+" — free text, not an enum; only ever shown to the super admin reviewing the application. */
    private String estimatedCatalogSize;

    /** The top-level Categories this enterprise is approved to list products under — assigned by the super admin at approval time, not chosen by the seller. */
    @ManyToMany
    @JoinTable(
            name = "enterprise_categories",
            joinColumns = @JoinColumn(name = "enterprise_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    @Builder.Default
    private Set<Category> categories = new HashSet<>();

    /** True for exactly one row — the enterprise every pre-existing (and future platform-owned) product belongs to. Used instead of hardcoding a UUID anywhere. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean platformDefault = false;

    private String approvedByName;

    private Instant approvedAt;

    // ── Where this shop's customers pay. Each seller receives and validates its own payments; the platform's own
    // store (platformDefault) starts with the numbers from application.properties. A code is the USSD string the
    // customer dials, with "{amount}" where the amount goes. A method is offered only when both its code and name are set.
    private String orangeMoneyCode;

    private String orangeMoneyName;

    private String mobileMoneyCode;

    private String mobileMoneyName;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
