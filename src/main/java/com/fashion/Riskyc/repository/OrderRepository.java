package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.Order;
import com.fashion.Riskyc.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);
    List<Order> findAllByOrderByCreatedAtDesc();
}
