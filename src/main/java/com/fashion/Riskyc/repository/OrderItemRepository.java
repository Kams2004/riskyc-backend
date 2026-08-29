package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    /**
     * Detaches every order line from a product before it's deleted — order
     * items keep their own snapshot of the name/price, so the order stays
     * intact and readable, it just no longer links to a live product row.
     */
    @Modifying
    @Query("update OrderItem oi set oi.product = null where oi.product.id = :productId")
    void detachProduct(@Param("productId") UUID productId);
}
