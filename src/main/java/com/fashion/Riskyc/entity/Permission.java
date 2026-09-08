package com.fashion.Riskyc.entity;

public enum Permission {
    VIEW_DASHBOARD,
    VIEW_ORDERS,
    MANAGE_ORDERS,
    VIEW_TREATMENT,
    MANAGE_TREATMENT,
    /** Sends the "your order has been packaged" confirmation to the customer — granted automatically to whoever can validate (MANAGE_ORDERS) or package (MANAGE_TREATMENT) an order, but assignable on its own too. */
    SEND_PACKAGING_MESSAGE,
    /** Add/edit/remove delivery-agent phone numbers — split out from MANAGE_TREATMENT so it can be reserved for the super admin (or anyone specifically granted it) instead of everyone who can pack orders. */
    MANAGE_DELIVERY_AGENTS,
    VIEW_PRODUCTS,
    MANAGE_PRODUCTS,
    VIEW_CATEGORIES,
    MANAGE_CATEGORIES,
    VIEW_CHAT,
    MANAGE_CHAT,
    VIEW_USERS,
    MANAGE_USERS,
    VIEW_CUSTOMERS,
    MANAGE_CUSTOMERS
}
