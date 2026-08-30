package com.fashion.Riskyc.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    @JsonBackReference
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageSender sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    /** MinIO object key for an attached photo — null for plain-text messages. */
    private String imageStorageKey;

    /** MinIO object key for an attached voice note — null for messages without one. */
    private String voiceStorageKey;

    /** Recorded length of the voice note, for showing "0:07" without downloading the file first. */
    private Integer voiceDurationSeconds;

    /**
     * Which staff member sent this (only set when {@code sender == ADMIN}
     * and the request carried a valid admin session). The customer-facing
     * widget never renders this — it always shows "Riskyc" — but the admin
     * inbox uses it so staff can see who answered.
     */
    private String adminSenderName;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant timestamp;
}
