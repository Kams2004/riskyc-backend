package com.fashion.Riskyc.entity;

/** Separate from PaymentMethod (online checkout) — a physical till needs CASH, which online orders never do. */
public enum PosPaymentMethod {
    CASH,
    ORANGE_MONEY,
    MOBILE_MONEY
}
