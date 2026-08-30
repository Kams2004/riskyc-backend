package com.fashion.Riskyc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** A "buy N for this total price" tier — independent of the product's regular unit price. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BulkPriceTier {

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;
}
