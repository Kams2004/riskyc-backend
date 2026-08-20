package com.fashion.Riskyc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket configuration used for real-time chat messages and
 * notifications, so the frontend never has to poll the REST API for updates.
 *
 * <p>Clients connect once to {@code /ws} and then:
 * <ul>
 *   <li>subscribe to {@code /topic/conversations/{conversationId}} to receive
 *       new chat messages as they're sent,</li>
 *   <li>subscribe to {@code /topic/notifications/{recipientId}} to receive
 *       admin/customer notifications (new order, new message, etc.),</li>
 *   <li>send outgoing chat messages to {@code /app/chat.send}.</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Native WebSocket clients
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins);
        // SockJS fallback for environments/proxies that block raw WebSocket upgrades
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins).withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }
}
