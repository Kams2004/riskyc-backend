package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.PosDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PosDeviceRepository extends JpaRepository<PosDevice, UUID> {
    List<PosDevice> findByEnterpriseIdOrderByEnrolledAtDesc(UUID enterpriseId);

    List<PosDevice> findByEnterpriseIsNullOrderByEnrolledAtDesc();

    List<PosDevice> findAllByOrderByEnrolledAtDesc();
}
