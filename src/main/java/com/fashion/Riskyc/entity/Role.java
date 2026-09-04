package com.fashion.Riskyc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Enumerated(EnumType.STRING)
    // Explicit columnDefinition, not just name — without it, Hibernate infers
    // the column type from the enum's *current* constants and bakes a CHECK
    // constraint listing them in at table-creation time. ddl-auto=update only
    // adds missing tables/columns on later boots, it never revisits that
    // constraint, so every permission added after the table first existed
    // would violate it and crash the app on startup (as SEND_PACKAGING_MESSAGE
    // just did). A plain varchar column carries no such constraint.
    @Column(name = "permission", columnDefinition = "varchar(64)")
    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
