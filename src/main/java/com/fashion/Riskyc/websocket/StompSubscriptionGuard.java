package com.fashion.Riskyc.websocket;

import com.fashion.Riskyc.entity.Permission;
import com.fashion.Riskyc.security.AdminPrincipal;
import com.fashion.Riskyc.security.JwtService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides who may listen to what on the {@code /ws} STOMP endpoint. The HTTP handshake itself stays open (customers
 * chat without an account), so the check happens per frame:
 * <ul>
 *   <li>CONNECT — an admin's client passes {@code Authorization: Bearer <jwt>} as a STOMP header; a valid token is
 *       remembered for the session. No/invalid token just means an anonymous session, not a refused connection.</li>
 *   <li>SUBSCRIBE — admin channels (every order with the customer's phone and address, admin notifications) need that
 *       admin, scoped to their own shop; a conversation's own channel stays open, the same as its REST read today.
 *       Anything else is dropped.</li>
 * </ul>
 * A refused subscription is dropped silently rather than answered with an ERROR frame — an error closes the whole
 * connection, and the same connection carries the page's other, allowed subscriptions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompSubscriptionGuard implements ChannelInterceptor {

    /** The raw admin token from CONNECT — re-verified on every SUBSCRIBE so an expired token stops opening admin channels. */
    private static final String TOKEN_ATTRIBUTE = "riskyc.adminToken";
    private static final String UUID_PATTERN = "[0-9a-fA-F-]{36}";
    private static final Pattern CONVERSATION = Pattern.compile("^/topic/conversations/" + UUID_PATTERN + "(/read)?$");
    private static final Pattern ADMIN_NOTIFICATIONS_FOR_SHOP = Pattern.compile("^/topic/notifications/admin/enterprise/(" + UUID_PATTERN + ")$");
    private static final Pattern ORDERS_FOR_SHOP = Pattern.compile("^/topic/orders/enterprise/(" + UUID_PATTERN + ")$");

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) return message;
        Map<String, Object> session = accessor.getSessionAttributes();

        if (accessor.getCommand() == StompCommand.CONNECT) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (session != null) {
                if (header != null && header.startsWith("Bearer ")) session.put(TOKEN_ATTRIBUTE, header.substring(7));
                else session.remove(TOKEN_ATTRIBUTE);
            }
            return message;
        }

        if (accessor.getCommand() == StompCommand.SEND) {
            // Messages from clients go to the app (/app/chat.send), never straight to a broker channel — otherwise anyone
            // could push a fake "shop" chat message or order update to every listener.
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith("/app/")) return message;
            log.debug("Dropped STOMP SEND to {}", destination);
            return null;
        }

        if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            AdminPrincipal admin = session != null ? adminFrom((String) session.get(TOKEN_ATTRIBUTE)) : null;
            String destination = accessor.getDestination();
            if (destination != null && mayListen(destination, admin)) return message;
            log.debug("Dropped STOMP subscription to {} (admin: {})", destination, admin != null ? admin.email() : "none");
            return null;
        }

        return message;
    }

    static boolean mayListen(String destination, AdminPrincipal admin) {
        if (destination.equals("/topic/sessions")) return true;
        if (CONVERSATION.matcher(destination).matches()) return true;
        if (admin == null) return false;

        boolean platform = admin.enterpriseId() == null;
        if (destination.equals("/topic/notifications/admin")) return platform;
        if (destination.equals("/topic/orders")) return platform && canSeeOrders(admin);

        Matcher shopNotifications = ADMIN_NOTIFICATIONS_FOR_SHOP.matcher(destination);
        if (shopNotifications.matches()) return platform || shopNotifications.group(1).equalsIgnoreCase(admin.enterpriseId());

        Matcher shopOrders = ORDERS_FOR_SHOP.matcher(destination);
        if (shopOrders.matches()) {
            return canSeeOrders(admin) && (platform || shopOrders.group(1).equalsIgnoreCase(admin.enterpriseId()));
        }
        return false;
    }

    private static boolean canSeeOrders(AdminPrincipal admin) {
        return admin.hasPermission(Permission.VIEW_ORDERS) || admin.hasPermission(Permission.VIEW_TREATMENT);
    }

    private AdminPrincipal adminFrom(String token) {
        if (token == null) return null;
        try {
            return jwtService.parse(token);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
