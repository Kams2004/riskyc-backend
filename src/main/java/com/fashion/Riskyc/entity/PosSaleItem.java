package com.fashion.Riskyc.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pos_sale_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosSaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    @JsonBackReference
    private PosSale sale;

    /** Kept even if the product is later deleted, so past sales stay intact — same convention as OrderItem.product. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private String selectedColor;

    @Column(nullable = false)
    private String selectedSize;

    /** Snapshot of the unit price at sale time — same reasoning as OrderItem.unitPrice. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /**
     * The variant's quantity immediately before this line decremented it —
     * captured once, at sale time, since the live stock number keeps moving
     * afterward and can't be reconstructed later. Null for sales made before
     * this field existed. oversoldBy = max(0, quantity - stockBeforeSale);
     * a line is a reconciliation candidate when that's > 0.
     */
    private Integer stockBeforeSale;

    @Column(nullable = false)
    @Builder.Default
    private boolean reconciled = false;

    private Instant reconciledAt;

    private String reconciledByName;
}
