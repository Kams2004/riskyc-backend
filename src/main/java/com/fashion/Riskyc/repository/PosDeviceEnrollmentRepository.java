package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.PosDeviceEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PosDeviceEnrollmentRepository extends JpaRepository<PosDeviceEnrollment, UUID> {
    Optional<PosDeviceEnrollment> findByCodeHash(String codeHash);
}
