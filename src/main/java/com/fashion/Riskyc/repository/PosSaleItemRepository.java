package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.PosSaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface PosSaleItemRepository extends JpaRepository<PosSaleItem, UUID> {

    /** Same as OrderItemRepository.detachProduct — till sale lines keep their own name/price snapshot. */
    @Modifying
    @Query("update PosSaleItem i set i.product = null where i.product.id = :productId")
    void detachProduct(@Param("productId") UUID productId);
}
