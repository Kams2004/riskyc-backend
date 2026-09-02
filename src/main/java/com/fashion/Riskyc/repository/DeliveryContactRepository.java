package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.DeliveryContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliveryContactRepository extends JpaRepository<DeliveryContact, UUID> {
    List<DeliveryContact> findAllByOrderByPositionAscCreatedAtAsc();
}
