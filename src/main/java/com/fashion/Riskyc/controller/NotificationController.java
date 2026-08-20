package com.fashion.Riskyc.controller;

import com.fashion.Riskyc.dto.response.NotificationResponse;
import com.fashion.Riskyc.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/admin")
    public List<NotificationResponse> listForAdmin() {
        return notificationService.listForAdmin();
    }

    @GetMapping("/admin/unread-count")
    public Map<String, Long> unreadCountForAdmin() {
        return Map.of("count", notificationService.countUnreadForAdmin());
    }

    @GetMapping("/customers/{customerId}")
    public List<NotificationResponse> listForCustomer(@PathVariable UUID customerId) {
        return notificationService.listForCustomer(customerId);
    }

    @GetMapping("/customers/{customerId}/unread-count")
    public Map<String, Long> unreadCountForCustomer(@PathVariable UUID customerId) {
        return Map.of("count", notificationService.countUnreadForCustomer(customerId));
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable UUID id) {
        return notificationService.markRead(id);
    }
}
