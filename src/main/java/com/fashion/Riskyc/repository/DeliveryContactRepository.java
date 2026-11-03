package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.DeliveryContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliveryContactRepository extends JpaRepository<DeliveryContact, UUID> {
    /** The platform's own roster (today's exact behavior). */
    List<DeliveryContact> findAllByEnterpriseIsNullOrderByPositionAscCreatedAtAsc();

    /** One enterprise's own roster. */
    List<DeliveryContact> findAllByEnterpriseIdOrderByPositionAscCreatedAtAsc(UUID enterpriseId);
}
