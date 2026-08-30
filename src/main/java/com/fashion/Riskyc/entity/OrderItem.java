package com.fashion.Riskyc.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonBackReference
    private Order order;

    /** Kept even if the product is later deleted, so past orders stay intact. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    private String selectedColor;

    private String selectedSize;

    /** Which of the product's photos (position in its media list at order time) this line was picked against — set only via the "quantity by photo" picker on colorless products. */
    private Integer selectedImageIndex;

    /** Snapshot of the unit price at purchase time — product prices can change later. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;
}
