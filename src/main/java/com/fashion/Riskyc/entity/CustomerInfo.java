package com.fashion.Riskyc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

/** Shipping/pickup details captured at checkout — embedded directly into {@link Order}. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerInfo {

    @Column(name = "info_first_name")
    private String firstName;

    @Column(name = "info_last_name")
    private String lastName;

    @Column(name = "info_phone")
    private String phone;

    @Column(name = "info_town")
    private String town;

    @Column(name = "info_street")
    private String street;

    @Enumerated(EnumType.STRING)
    @Column(name = "info_delivery_type")
    private DeliveryType deliveryType;
}
