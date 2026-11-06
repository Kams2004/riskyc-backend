package com.fashion.Riskyc.dto.response;

import com.fashion.Riskyc.entity.PaymentMethod;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Checkout's "who do I pay, how much, and how" — one entry per shop in the cart (see OrderService#paymentPlan). */
public record PaymentPlanResponse(List<Seller> sellers) {

    /** methods is empty (and acceptsPayments false) when the shop hasn't set up its payment numbers yet. */
    public record Seller(UUID enterpriseId, String sellerName, BigDecimal amount, boolean acceptsPayments, List<Method> methods) {}

    /** code is the dial code with this shop's amount already filled in. */
    public record Method(PaymentMethod method, String code, String accountName) {}
}
