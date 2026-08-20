package com.fashion.Riskyc.controller;

import com.fashion.Riskyc.dto.request.SendMessageRequest;
import com.fashion.Riskyc.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/**
 * STOMP entry point for outgoing chat messages. A client connected to
 * {@code /ws} sends here (destination {@code /app/chat.send}); the message
 * is persisted and then broadcast to every subscriber of
 * {@code /topic/conversations/{conversationId}} by {@link ConversationService}
 * — including the sender's own other tabs/devices.
 */
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ConversationService conversationService;

    @MessageMapping("/chat.send")
    public void send(@Payload SendMessageRequest request) {
        conversationService.sendMessage(request);
    }
}
