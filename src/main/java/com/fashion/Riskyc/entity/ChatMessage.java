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

    /**
     * True for the special "your order has been packaged" message an admin sends from the
     * order detail page — rendered as its own card on the tracking page, with the delivery
     * team's contact info appended automatically.
     *
     * <p>{@code columnDefinition} carries an explicit DB-level default — without one, adding
     * this NOT NULL column to the already-populated {@code chat_message} table on an existing
     * deployment (ddl-auto=update) fails outright ("column contains null values"), silently
     * leaving the column missing and breaking every chat insert, not just this feature's.
     */
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    @Builder.Default
    private boolean packagingConfirmation = false;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant timestamp;
}
