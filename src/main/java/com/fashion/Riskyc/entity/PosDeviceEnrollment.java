package com.fashion.Riskyc.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** A short-lived, single-use code an admin generates so a new till can enrol itself under their shop. */
@Entity
@Table(name = "pos_device_enrollments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosDeviceEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** SHA-256 (hex) of the code, so a database read never reveals a usable code. */
    @Column(nullable = false, unique = true, length = 64)
    private String codeHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enterprise_id")
    private Enterprise enterprise;

    private String createdByName;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;
}
