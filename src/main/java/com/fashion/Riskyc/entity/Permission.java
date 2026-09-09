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
    /** Create a brand-new product — deliberately all-or-nothing (a new product needs at least a name and a picture to exist), unlike the section-scoped UPDATE_PRODUCT_* permissions below. */
    CREATE_PRODUCT,
    DELETE_PRODUCT,
    /** Name, description, category/subcategory. */
    UPDATE_PRODUCT_INFO,
    /** Price, original price, bulk-price tiers. */
    UPDATE_PRODUCT_PRICING,
    /** Media upload/reorder/delete. */
    UPDATE_PRODUCT_IMAGES,
    /** A color's name/hex swatch, and adding/removing color entries — NOT its stock count, see UPDATE_PRODUCT_STOCK. */
    UPDATE_PRODUCT_COLORS,
    /** The stock count of an *existing* color — split from UPDATE_PRODUCT_COLORS so e.g. a warehouse role can adjust quantities without being able to rename or add/remove color options. */
    UPDATE_PRODUCT_STOCK,
    /** Badge, sizes, rating, review count. */
    UPDATE_PRODUCT_DISPLAY,
    /** The Visible/Hidden toggle specifically — split out so it can be delegated on its own. */
    UPDATE_PRODUCT_VISIBILITY,
    VIEW_CATEGORIES,
    MANAGE_CATEGORIES,
    VIEW_CHAT,
    MANAGE_CHAT,
    VIEW_USERS,
    MANAGE_USERS,
    VIEW_CUSTOMERS,
    MANAGE_CUSTOMERS
}
