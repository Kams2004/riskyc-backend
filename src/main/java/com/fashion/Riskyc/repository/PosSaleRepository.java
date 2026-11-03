package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.PosSale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PosSaleRepository extends JpaRepository<PosSale, UUID> {
    /** The idempotency lookup — see PosSale's own javadoc on clientSaleId. */
    Optional<PosSale> findByClientSaleId(UUID clientSaleId);

    List<PosSale> findAllByOrderBySoldAtDesc();

    List<PosSale> findAllByStockReconciliationFlagTrueOrderBySoldAtDesc();

    /** Scoped sales listing — used when the acting admin isn't platform staff. */
    List<PosSale> findByEnterpriseIdOrderBySoldAtDesc(UUID enterpriseId);

    List<PosSale> findByEnterpriseIdAndStockReconciliationFlagTrueOrderBySoldAtDesc(UUID enterpriseId);

    /** For DataSeeder's one-time backfill onto the platform-default enterprise. */
    List<PosSale> findByEnterpriseIsNull();
}
