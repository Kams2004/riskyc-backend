package com.fashion.Riskyc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** A member of the delivery team, shown to customers in a packaging-confirmation message so they know who might collect/deliver their order. */
@Entity
@Table(name = "delivery_contacts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryContact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String phone;

    /** Display order — lower shows first. */
    @Column(nullable = false)
    @Builder.Default
    private int position = 0;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
