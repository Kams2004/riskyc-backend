package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.ProductMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductMediaRepository extends JpaRepository<ProductMedia, UUID> {
    List<ProductMedia> findByProductId(UUID productId);
}
