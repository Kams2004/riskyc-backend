package com.fashion.Riskyc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;

    /** BCrypt hash. */
    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CustomerStatus status = CustomerStatus.ACTIVE;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    private Instant lastLogin;

    /**
     * Short shareable code this customer hands out to friends. Nullable at the
     * JPA level (rather than {@code nullable = false}) purely so {@code ddl-auto=update}
     * can add the column to an existing non-empty table — every code path that
     * reads a Customer backfills it lazily if missing (see CustomerService.ensureReferralFields).
     */
    @Column(unique = true)
    private String referralCode;

    /** Display name shown to people this customer referred (and vice versa) — never the real name. */
    private String acronym;

    /** Who referred this customer in, if anyone — resolved one level at signup and never changed after. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_by_id")
    private Customer referredBy;

    /** Google "sub" claim, set only for accounts created/linked via Sign in with Google. */
    @Column(unique = true)
    private String googleId;
}
