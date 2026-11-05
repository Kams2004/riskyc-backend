package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    Optional<Customer> findByReferralCodeIgnoreCase(String referralCode);
    boolean existsByReferralCode(String referralCode);

    List<Customer> findByReferredByIdOrderByCreatedAtDesc(UUID referredById);
    long countByReferredById(UUID referredById);

    Optional<Customer> findByGoogleId(String googleId);

    @Modifying
    @Query("update Customer c set c.referredBy = null where c.referredBy.id = :customerId")
    void detachReferrals(UUID customerId);
}
