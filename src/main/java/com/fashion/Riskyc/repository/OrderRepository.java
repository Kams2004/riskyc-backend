package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.Order;
import com.fashion.Riskyc.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);
    List<Order> findAllByOrderByCreatedAtDesc();

    /**
     * Row-locks the order for the rest of the transaction — used by
     * startPackaging so two admins claiming the same order at the same
     * moment are serialized instead of both reading VALIDATED and both
     * "winning" (the second's write silently clobbering the first's).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(UUID id);
}
