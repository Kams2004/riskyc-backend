package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.Enterprise;
import com.fashion.Riskyc.entity.EnterpriseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnterpriseRepository extends JpaRepository<Enterprise, UUID> {

    Optional<Enterprise> findByPlatformDefaultTrue();

    List<Enterprise> findAllByStatusOrderByCreatedAtDesc(EnterpriseStatus status);

    List<Enterprise> findAllByOrderByCreatedAtDesc();

    /** Subcategory ownership check: can this enterprise touch the given (top-level) category? */
    boolean existsByIdAndCategories_Id(UUID enterpriseId, UUID categoryId);
}
