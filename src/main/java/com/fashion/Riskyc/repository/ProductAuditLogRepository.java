package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.ProductAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductAuditLogRepository extends JpaRepository<ProductAuditLog, UUID> {

    Page<ProductAuditLog> findByProductIdOrderByChangedAtDesc(UUID productId, Pageable pageable);

    Page<ProductAuditLog> findAllByOrderByChangedAtDesc(Pageable pageable);
}
