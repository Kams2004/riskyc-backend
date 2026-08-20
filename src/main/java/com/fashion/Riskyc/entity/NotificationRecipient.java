package com.fashion.Riskyc.entity;

/** Who a {@link Notification} is broadcast to. */
public enum NotificationRecipient {
    /** Every connected admin/staff session (order desk, support). */
    ADMIN,
    /** A single customer, identified by {@link Notification#getCustomer()}. */
    CUSTOMER
}
