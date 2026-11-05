package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.response.NotificationResponse;
import com.fashion.Riskyc.entity.Customer;
import com.fashion.Riskyc.entity.Notification;
import com.fashion.Riskyc.entity.NotificationRecipient;
import com.fashion.Riskyc.entity.NotificationType;
import com.fashion.Riskyc.repository.CustomerRepository;
import com.fashion.Riskyc.repository.NotificationRepository;
import com.fashion.Riskyc.exception.ForbiddenException;
import com.fashion.Riskyc.exception.ResourceNotFoundException;
import com.fashion.Riskyc.repository.EnterpriseRepository;
import com.fashion.Riskyc.security.CurrentAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Persists notifications and pushes them over WebSocket/STOMP the instant
 * they happen, so the admin bell icon and a customer's order-status toast
 * update live instead of the client having to poll a REST endpoint.
 *
 * <p>Topics used (no auth/session principal is wired up yet, so these are
 * plain public topics rather than {@code convertAndSendToUser}):
 * <ul>
 *   <li>{@code /topic/notifications/admin} — every connected admin session</li>
 *   <li>{@code /topic/notifications/customers/{customerId}} — one customer</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final CustomerRepository customerRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /** A platform-level admin notification — only platform staff (no seller scope) see it. */
    @Transactional
    public NotificationResponse notifyAdmin(NotificationType type, String message, String referenceId) {
        return notifyAdmin(type, message, referenceId, null);
    }

    /**
     * An admin notification about one seller's order: that seller's admins see it (live on
     * /topic/notifications/admin/enterprise/{id}), and platform staff see everything on /topic/notifications/admin.
     */
    @Transactional
    public NotificationResponse notifyAdmin(NotificationType type, String message, String referenceId, UUID enterpriseId) {
        Notification saved = notificationRepository.saveAndFlush(Notification.builder()
                .recipientType(NotificationRecipient.ADMIN)
                .enterprise(enterpriseId != null ? enterpriseRepository.getReferenceById(enterpriseId) : null)
                .type(type)
                .message(message)
                .referenceId(referenceId)
                .build());
        NotificationResponse response = toResponse(saved);
        messagingTemplate.convertAndSend("/topic/notifications/admin", response);
        if (enterpriseId != null) {
            messagingTemplate.convertAndSend("/topic/notifications/admin/enterprise/" + enterpriseId, response);
        }
        return response;
    }

    @Transactional
    public NotificationResponse notifyCustomer(UUID customerId, NotificationType type, String message, String referenceId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", customerId));
        Notification saved = notificationRepository.saveAndFlush(Notification.builder()
                .recipientType(NotificationRecipient.CUSTOMER)
                .customer(customer)
                .type(type)
                .message(message)
                .referenceId(referenceId)
                .build());
        NotificationResponse response = toResponse(saved);
        messagingTemplate.convertAndSend("/topic/notifications/customers/" + customerId, response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listForAdmin() {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        List<Notification> list = scopeId == null
                ? notificationRepository.findByRecipientTypeOrderByCreatedAtDesc(NotificationRecipient.ADMIN)
                : notificationRepository.findByRecipientTypeAndEnterpriseIdOrderByCreatedAtDesc(NotificationRecipient.ADMIN, scopeId);
        return list.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listForCustomer(UUID customerId) {
        return notificationRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public long countUnreadForAdmin() {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        return scopeId == null
                ? notificationRepository.countByRecipientTypeAndReadFalse(NotificationRecipient.ADMIN)
                : notificationRepository.countByRecipientTypeAndEnterpriseIdAndReadFalse(NotificationRecipient.ADMIN, scopeId);
    }

    @Transactional(readOnly = true)
    public long countUnreadForCustomer(UUID customerId) {
        return notificationRepository.countByCustomerIdAndReadFalse(customerId);
    }

    @Transactional
    public NotificationResponse markRead(UUID id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", id));
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        if (scopeId != null && (notification.getEnterprise() == null || !scopeId.equals(notification.getEnterprise().getId()))) {
            throw new ForbiddenException("This notification belongs to a different seller.");
        }
        notification.setRead(true);
        return toResponse(notification);
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getRecipientType(),
                n.getCustomer() != null ? n.getCustomer().getId() : null,
                n.getType(),
                n.getMessage(),
                n.getReferenceId(),
                n.isRead(),
                n.getCreatedAt()
        );
    }
}
