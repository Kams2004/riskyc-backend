package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.Notification;
import com.fashion.Riskyc.entity.NotificationRecipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByRecipientTypeOrderByCreatedAtDesc(NotificationRecipient recipientType);

    List<Notification> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    long countByRecipientTypeAndReadFalse(NotificationRecipient recipientType);

    long countByCustomerIdAndReadFalse(UUID customerId);

    List<Notification> findByRecipientTypeAndEnterpriseIdOrderByCreatedAtDesc(NotificationRecipient recipientType, UUID enterpriseId);

    long countByRecipientTypeAndEnterpriseIdAndReadFalse(NotificationRecipient recipientType, UUID enterpriseId);

    @Modifying
    @Query("delete from Notification n where n.customer.id = :customerId")
    void deleteByCustomerId(UUID customerId);
}
