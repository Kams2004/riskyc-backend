package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    /** Platform-wide roles only (enterprise is null) — the pre-existing global duplicate-name check. */
    boolean existsByNameIgnoreCaseAndEnterpriseIsNull(String name);

    /** Duplicate-name check scoped to one enterprise's own roles. */
    boolean existsByNameIgnoreCaseAndEnterpriseId(String name, UUID enterpriseId);

    List<Role> findByEnterpriseIsNull();

    List<Role> findByEnterpriseId(UUID enterpriseId);
}
