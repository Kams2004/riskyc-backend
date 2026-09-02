package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    List<Conversation> findAllByOrderByLastMessageAtDesc();
    List<Conversation> findByCustomerIdOrderByLastMessageAtDesc(UUID customerId);
    Optional<Conversation> findByOrder_Id(UUID orderId);
}
