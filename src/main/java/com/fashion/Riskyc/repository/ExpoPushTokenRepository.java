package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.ExpoPushToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpoPushTokenRepository extends JpaRepository<ExpoPushToken, UUID> {

    List<ExpoPushToken> findByOrderId(UUID orderId);

    Optional<ExpoPushToken> findByToken(String token);

    void deleteByToken(String token);
}
