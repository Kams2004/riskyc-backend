package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.request.CreateConversationRequest;
import com.fashion.Riskyc.dto.request.SendMessageRequest;
import com.fashion.Riskyc.dto.response.ChatMessageResponse;
import com.fashion.Riskyc.dto.response.ConversationResponse;
import com.fashion.Riskyc.entity.*;
import com.fashion.Riskyc.exception.ResourceNotFoundException;
import com.fashion.Riskyc.repository.ChatMessageRepository;
import com.fashion.Riskyc.repository.ConversationRepository;
import com.fashion.Riskyc.repository.CustomerRepository;
import com.fashion.Riskyc.repository.OrderRepository;
import com.fashion.Riskyc.security.CurrentAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Backs the admin/customer chat. Every new message is persisted and pushed
 * immediately to {@code /topic/conversations/{conversationId}} — subscribed
 * clients (the {@code useMessageSocket}-style hook on the frontend) receive
 * it in real time instead of polling.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;
    private final S3MediaService s3MediaService;

    private static final String CHAT_IMAGE_FOLDER = "chat";
    private static final String CHAT_VOICE_FOLDER = "chat-voice";

    @Transactional(readOnly = true)
    public List<ConversationResponse> listAll() {
        return conversationRepository.findAllByOrderByLastMessageAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ConversationResponse getById(UUID id) {
        return toResponse(getOrThrow(id));
    }

    /** Lets a logged-in customer's chat widget find the single thread admins may already be using to reach them. */
    @Transactional(readOnly = true)
    public ConversationResponse getByCustomerId(UUID customerId) {
        return conversationRepository.findByCustomerIdOrderByLastMessageAtDesc(customerId).stream()
                .findFirst()
                .map(this::toResponse)
                .orElse(null);
    }

    public ConversationResponse create(CreateConversationRequest request) {
        Customer customer = request.customerId() != null
                ? customerRepository.findById(request.customerId())
                        .orElseThrow(() -> ResourceNotFoundException.of("Customer", request.customerId()))
                : null;
        Order order = request.orderId() != null
                ? orderRepository.findById(request.orderId())
                        .orElseThrow(() -> ResourceNotFoundException.of("Order", request.orderId()))
                : null;

        Conversation conversation = conversationRepository.saveAndFlush(Conversation.builder()
                .customerName(request.customerName())
                .customer(customer)
                .order(order)
                .lastMessageAt(Instant.now())
                .build());
        return toResponse(conversation);
    }

    public ChatMessageResponse sendMessage(SendMessageRequest request) {
        Conversation conversation = getOrThrow(request.conversationId());

        ChatMessage message = ChatMessage.builder()
                .conversation(conversation)
                .sender(request.sender())
                .text(request.text())
                .adminSenderName(request.sender() == MessageSender.ADMIN ? CurrentAdmin.nameOrNull() : null)
                .build();
        // Flush so @CreationTimestamp populates message.timestamp before we
        // serialize and broadcast it — dirty-checking alone wouldn't apply
        // it until the transaction commits, after this method has returned.
        message = chatMessageRepository.saveAndFlush(message);
        conversation.getMessages().add(message);
        conversation.setLastMessageAt(Instant.now());
        if (request.sender() == MessageSender.CUSTOMER) {
            conversation.setUnread(conversation.getUnread() + 1);
        }

        ChatMessageResponse response = toResponse(message);
        messagingTemplate.convertAndSend("/topic/conversations/" + conversation.getId(), response);

        if (request.sender() == MessageSender.CUSTOMER) {
            notificationService.notifyAdmin(NotificationType.NEW_MESSAGE,
                    conversation.getCustomerName() + " sent a new message", conversation.getId().toString());
        } else if (conversation.getCustomer() != null) {
            notificationService.notifyCustomer(conversation.getCustomer().getId(), NotificationType.NEW_MESSAGE,
                    "You have a new message from support", conversation.getId().toString());
        }

        return response;
    }

    public ChatMessageResponse sendImageMessage(UUID conversationId, MessageSender sender, String text, MultipartFile file) {
        Conversation conversation = getOrThrow(conversationId);
        String key = s3MediaService.upload(file, CHAT_IMAGE_FOLDER + "/" + conversationId);

        ChatMessage message = ChatMessage.builder()
                .conversation(conversation)
                .sender(sender)
                .text(text != null ? text : "")
                .imageStorageKey(key)
                .adminSenderName(sender == MessageSender.ADMIN ? CurrentAdmin.nameOrNull() : null)
                .build();
        message = chatMessageRepository.saveAndFlush(message);
        conversation.getMessages().add(message);
        conversation.setLastMessageAt(Instant.now());
        if (sender == MessageSender.CUSTOMER) {
            conversation.setUnread(conversation.getUnread() + 1);
        }

        ChatMessageResponse response = toResponse(message);
        messagingTemplate.convertAndSend("/topic/conversations/" + conversation.getId(), response);

        if (sender == MessageSender.CUSTOMER) {
            notificationService.notifyAdmin(NotificationType.NEW_MESSAGE,
                    conversation.getCustomerName() + " sent a photo", conversation.getId().toString());
        } else if (conversation.getCustomer() != null) {
            notificationService.notifyCustomer(conversation.getCustomer().getId(), NotificationType.NEW_MESSAGE,
                    "You have a new message from support", conversation.getId().toString());
        }

        return response;
    }

    public ChatMessageResponse sendVoiceMessage(UUID conversationId, MessageSender sender, Integer durationSeconds, MultipartFile file) {
        Conversation conversation = getOrThrow(conversationId);
        String key = s3MediaService.upload(file, CHAT_VOICE_FOLDER + "/" + conversationId);

        ChatMessage message = ChatMessage.builder()
                .conversation(conversation)
                .sender(sender)
                .text("")
                .voiceStorageKey(key)
                .voiceDurationSeconds(durationSeconds)
                .adminSenderName(sender == MessageSender.ADMIN ? CurrentAdmin.nameOrNull() : null)
                .build();
        message = chatMessageRepository.saveAndFlush(message);
        conversation.getMessages().add(message);
        conversation.setLastMessageAt(Instant.now());
        if (sender == MessageSender.CUSTOMER) {
            conversation.setUnread(conversation.getUnread() + 1);
        }

        ChatMessageResponse response = toResponse(message);
        messagingTemplate.convertAndSend("/topic/conversations/" + conversation.getId(), response);

        if (sender == MessageSender.CUSTOMER) {
            notificationService.notifyAdmin(NotificationType.NEW_MESSAGE,
                    conversation.getCustomerName() + " sent a voice message", conversation.getId().toString());
        } else if (conversation.getCustomer() != null) {
            notificationService.notifyCustomer(conversation.getCustomer().getId(), NotificationType.NEW_MESSAGE,
                    "You have a new message from support", conversation.getId().toString());
        }

        return response;
    }

    public void markRead(UUID conversationId) {
        Conversation conversation = getOrThrow(conversationId);
        conversation.setUnread(0);
    }

    public void delete(UUID conversationId) {
        Conversation conversation = getOrThrow(conversationId);
        conversation.getMessages().forEach(m -> {
            if (m.getImageStorageKey() != null) s3MediaService.delete(m.getImageStorageKey());
            if (m.getVoiceStorageKey() != null) s3MediaService.delete(m.getVoiceStorageKey());
        });
        conversationRepository.delete(conversation);
    }

    private Conversation getOrThrow(UUID id) {
        return conversationRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Conversation", id));
    }

    private ConversationResponse toResponse(Conversation c) {
        List<ChatMessageResponse> messages = c.getMessages().stream().map(this::toResponse).toList();
        return new ConversationResponse(
                c.getId(),
                c.getCustomerName(),
                c.getCustomer() != null ? c.getCustomer().getId() : null,
                c.getOrder() != null ? c.getOrder().getId() : null,
                messages,
                c.getUnread(),
                c.getCreatedAt(),
                c.getLastMessageAt()
        );
    }

    private ChatMessageResponse toResponse(ChatMessage m) {
        String imageUrl = m.getImageStorageKey() != null ? s3MediaService.getPresignedUrl(m.getImageStorageKey()) : null;
        String voiceUrl = m.getVoiceStorageKey() != null ? s3MediaService.getPresignedUrl(m.getVoiceStorageKey()) : null;
        return new ChatMessageResponse(m.getId(), m.getConversation().getId(), m.getSender(), m.getText(), imageUrl, voiceUrl, m.getVoiceDurationSeconds(), m.getAdminSenderName(), m.getTimestamp());
    }
}
