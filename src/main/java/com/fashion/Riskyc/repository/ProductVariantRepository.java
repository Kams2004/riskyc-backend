package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.ProductVariant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {
    List<ProductVariant> findByProductIdOrderByColorNameAscSizeAsc(UUID productId);
    Optional<ProductVariant> findByProductIdAndColorNameAndSize(UUID productId, String colorName, String size);
    boolean existsByProductId(UUID productId);
    boolean existsByProductIdAndUntrackedFalse(UUID productId);

    @Modifying
    @Query("delete from ProductVariant v where v.product.id = :productId")
    void deleteByProductId(UUID productId);

    /** Mirrors OrderRepository.findByIdForUpdate exactly — same lock, same reasoning: two concurrent sales touching the same variant must be serialized, not both read-then-write past each other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariant v where v.id = :id")
    Optional<ProductVariant> findByIdForUpdate(UUID id);
}
