package com.fashion.Riskyc.controller;

import com.fashion.Riskyc.dto.request.CreateConversationRequest;
import com.fashion.Riskyc.dto.request.SendMessageRequest;
import com.fashion.Riskyc.dto.response.ChatMessageResponse;
import com.fashion.Riskyc.dto.response.ConversationResponse;
import com.fashion.Riskyc.entity.MessageSender;
import com.fashion.Riskyc.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * REST surface for conversation history (initial load, admin inbox list).
 * New messages sent while a client is connected go through the STOMP
 * endpoint in {@link ChatWebSocketController} instead, for instant delivery.
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    public List<ConversationResponse> list() {
        return conversationService.listAll();
    }

    @GetMapping("/{id}")
    public ConversationResponse getById(@PathVariable UUID id) {
        return conversationService.getById(id);
    }

    /** Lets a logged-in customer's chat widget find its existing thread, even if an admin started it first. */
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ConversationResponse> getByCustomerId(@PathVariable UUID customerId) {
        ConversationResponse response = conversationService.getByCustomerId(customerId);
        return response != null ? ResponseEntity.ok(response) : ResponseEntity.noContent().build();
    }

    /** Lets a guest's tracking page (no account, so no customerId to look up by) find its order's thread and adopt it into the chat widget. */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<ConversationResponse> getByOrderId(@PathVariable UUID orderId) {
        ConversationResponse response = conversationService.getByOrderId(orderId);
        return response != null ? ResponseEntity.ok(response) : ResponseEntity.noContent().build();
    }

    /** Admin-only: sends the "your order has been packaged" confirmation — text and/or a photo, with the delivery team's contact info appended automatically. */
    @PostMapping("/order/{orderId}/packaging-confirmation")
    public ChatMessageResponse sendPackagingConfirmation(
            @PathVariable UUID orderId,
            @RequestParam(value = "text", required = false) String text,
            @RequestParam(value = "file", required = false) MultipartFile file
    ) {
        return conversationService.sendPackagingConfirmation(orderId, text, file);
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> create(@Valid @RequestBody CreateConversationRequest request) {
        return ResponseEntity.status(201).body(conversationService.create(request));
    }

    /** REST fallback for sending a message (e.g. before the WebSocket connects). */
    @PostMapping("/messages")
    public ChatMessageResponse sendMessage(@Valid @RequestBody SendMessageRequest request) {
        return conversationService.sendMessage(request);
    }

    /** Sends a message with an attached photo — uploaded via multipart since STOMP carries text payloads only. */
    @PostMapping("/{id}/messages/image")
    public ChatMessageResponse sendImageMessage(
            @PathVariable UUID id,
            @RequestParam("sender") MessageSender sender,
            @RequestParam(value = "text", required = false) String text,
            @RequestParam("file") MultipartFile file
    ) {
        return conversationService.sendImageMessage(id, sender, text, file);
    }

    /** Sends a message with an attached voice note — same multipart approach as the photo endpoint. */
    @PostMapping("/{id}/messages/voice")
    public ChatMessageResponse sendVoiceMessage(
            @PathVariable UUID id,
            @RequestParam("sender") MessageSender sender,
            @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds,
            @RequestParam("file") MultipartFile file
    ) {
        return conversationService.sendVoiceMessage(id, sender, durationSeconds, file);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id) {
        conversationService.markRead(id);
        return ResponseEntity.noContent().build();
    }

    /** Customer-side equivalent — called when the customer opens/views the chat widget, so admins see their sent messages tick as read. */
    @PostMapping("/{id}/read-by-customer")
    public ResponseEntity<Void> markReadByCustomer(@PathVariable UUID id) {
        conversationService.markReadByCustomer(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        conversationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
