package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findByConversationIdOrderByTimestampAsc(UUID conversationId);
    Optional<ChatMessage> findFirstByConversationIdAndPackagingConfirmationTrueOrderByTimestampDesc(UUID conversationId);
}
