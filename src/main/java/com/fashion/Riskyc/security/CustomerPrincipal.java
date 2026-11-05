package com.fashion.Riskyc.security;

import java.util.UUID;

/** A signed-in shop customer, from a customer token — see {@link JwtService#generateCustomerToken(UUID)}. */
public record CustomerPrincipal(UUID customerId) {
    public static final String AUTHORITY = "ROLE_CUSTOMER";
}
