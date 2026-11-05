package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    List<Conversation> findAllByOrderByLastMessageAtDesc();
    List<Conversation> findByCustomerIdOrderByLastMessageAtDesc(UUID customerId);
    Optional<Conversation> findByOrder_Id(UUID orderId);

    /** Conversations keep customerName, so the chat history stays readable after the account is deleted. */
    @Modifying
    @Query("update Conversation c set c.customer = null where c.customer.id = :customerId")
    void detachCustomer(UUID customerId);
}
