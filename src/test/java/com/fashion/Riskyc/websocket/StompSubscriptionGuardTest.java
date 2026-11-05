package com.fashion.Riskyc.websocket;

import com.fashion.Riskyc.entity.Permission;
import com.fashion.Riskyc.security.AdminPrincipal;
import com.fashion.Riskyc.config.JwtProperties;
import com.fashion.Riskyc.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StompSubscriptionGuardTest {

    private static final String SHOP = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER_SHOP = "22222222-2222-2222-2222-222222222222";
    private static final String CONVERSATION = "33333333-3333-3333-3333-333333333333";

    private static AdminPrincipal admin(String enterpriseId, Permission... permissions) {
        return new AdminPrincipal("u", "a@x.com", "A", "r", "Role", Set.of(permissions), enterpriseId);
    }

    @Test
    void anonymousOnlyGetsConversationAndPresenceChannels() {
        assertTrue(StompSubscriptionGuard.mayListen("/topic/conversations/" + CONVERSATION, null));
        assertTrue(StompSubscriptionGuard.mayListen("/topic/conversations/" + CONVERSATION + "/read", null));
        assertTrue(StompSubscriptionGuard.mayListen("/topic/sessions", null));

        assertFalse(StompSubscriptionGuard.mayListen("/topic/orders", null));
        assertFalse(StompSubscriptionGuard.mayListen("/topic/notifications/admin", null));
        assertFalse(StompSubscriptionGuard.mayListen("/topic/notifications/admin/enterprise/" + SHOP, null));
        assertFalse(StompSubscriptionGuard.mayListen("/topic/orders/enterprise/" + SHOP, null));
        assertFalse(StompSubscriptionGuard.mayListen("/topic/notifications/customers/" + SHOP, null));
        assertFalse(StompSubscriptionGuard.mayListen("/topic/conversations/not-a-uuid", null));
        assertFalse(StompSubscriptionGuard.mayListen("/topic/**", null));
    }

    @Test
    void platformStaffSeeEverythingTheirPermissionsAllow() {
        AdminPrincipal platform = admin(null, Permission.VIEW_ORDERS);
        assertTrue(StompSubscriptionGuard.mayListen("/topic/orders", platform));
        assertTrue(StompSubscriptionGuard.mayListen("/topic/notifications/admin", platform));
        assertTrue(StompSubscriptionGuard.mayListen("/topic/notifications/admin/enterprise/" + SHOP, platform));

        assertFalse(StompSubscriptionGuard.mayListen("/topic/orders", admin(null, Permission.VIEW_CHAT)));
        assertTrue(StompSubscriptionGuard.mayListen("/topic/orders", admin(null, Permission.VIEW_TREATMENT)));
    }

    @Test
    void sellerAdminsOnlyHearTheirOwnShop() {
        AdminPrincipal seller = admin(SHOP, Permission.VIEW_ORDERS);
        assertTrue(StompSubscriptionGuard.mayListen("/topic/orders/enterprise/" + SHOP, seller));
        assertTrue(StompSubscriptionGuard.mayListen("/topic/notifications/admin/enterprise/" + SHOP, seller));

        assertFalse(StompSubscriptionGuard.mayListen("/topic/orders", seller));
        assertFalse(StompSubscriptionGuard.mayListen("/topic/notifications/admin", seller));
        assertFalse(StompSubscriptionGuard.mayListen("/topic/orders/enterprise/" + OTHER_SHOP, seller));
        assertFalse(StompSubscriptionGuard.mayListen("/topic/notifications/admin/enterprise/" + OTHER_SHOP, seller));
    }

    private static Message<byte[]> send(String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(destination);
        accessor.setSessionAttributes(new HashMap<>());
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage("{\"sender\":\"ADMIN\",\"text\":\"pay here\"}".getBytes(), accessor.getMessageHeaders());
    }

    @Test
    void clientsCannotPublishStraightToABrokerChannel() {
        StompSubscriptionGuard guard = new StompSubscriptionGuard(new JwtService(new JwtProperties()));
        assertNull(guard.preSend(send("/topic/conversations/" + CONVERSATION), null), "a fake shop message must not reach listeners");
        assertNull(guard.preSend(send("/topic/orders"), null));
        assertNull(guard.preSend(send("/queue/anything"), null));
        assertNotNull(guard.preSend(send("/app/chat.send"), null), "the app endpoint still receives messages");
    }
}
