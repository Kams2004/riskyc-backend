package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.PosSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** One row per seller's till, plus one row (enterprise null) for the platform's own till. */
public interface PosSettingsRepository extends JpaRepository<PosSettings, UUID> {
    Optional<PosSettings> findFirstByEnterpriseId(UUID enterpriseId);

    Optional<PosSettings> findFirstByEnterpriseIsNull();
}
