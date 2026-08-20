package com.fashion.Riskyc.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String customerName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("timestamp ASC")
    @Builder.Default
    @JsonManagedReference
    private List<ChatMessage> messages = new ArrayList<>();

    /** Number of messages the admin hasn't opened yet. */
    @Column(nullable = false)
    @Builder.Default
    private int unread = 0;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    private Instant lastMessageAt;
}
