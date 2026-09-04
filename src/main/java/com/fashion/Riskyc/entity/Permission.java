package com.fashion.Riskyc.entity;

public enum Permission {
    VIEW_DASHBOARD,
    VIEW_ORDERS,
    MANAGE_ORDERS,
    VIEW_TREATMENT,
    MANAGE_TREATMENT,
    /** Sends the "your order has been packaged" confirmation to the customer — granted automatically to whoever can validate (MANAGE_ORDERS) or package (MANAGE_TREATMENT) an order, but assignable on its own too. */
    SEND_PACKAGING_MESSAGE,
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
