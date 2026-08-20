package com.fashion.Riskyc.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks how many STOMP sessions are currently connected and broadcasts the
 * live count to {@code /topic/sessions} — backs a presence indicator
 * ("N people online") without any client having to poll for it.
 */
@Component
@Slf4j
public class WebSocketSessionListener {

    private final Map<String, Boolean> activeSessions = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketSessionListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        String sessionId = sessionIdOf(event);
        if (sessionId != null) {
            activeSessions.put(sessionId, Boolean.TRUE);
            broadcastCount();
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        activeSessions.remove(event.getSessionId());
        broadcastCount();
    }

    private record SessionCount(int online) {
    }

    private void broadcastCount() {
        messagingTemplate.convertAndSend("/topic/sessions", new SessionCount(activeSessions.size()));
    }

    private String sessionIdOf(SessionConnectedEvent event) {
        Object header = event.getMessage().getHeaders().get("simpSessionId");
        return header != null ? header.toString() : null;
    }
}
