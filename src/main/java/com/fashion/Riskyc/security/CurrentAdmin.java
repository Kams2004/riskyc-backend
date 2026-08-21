package com.fashion.Riskyc.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Reads the authenticated admin (if any) straight from the security context,
 * so services can stamp "who did this" on an action without every controller
 * having to thread an {@code AdminPrincipal} parameter through. Works even on
 * endpoints that are {@code permitAll} for guests — if the caller happened to
 * attach a valid admin bearer token, it's picked up; otherwise this is empty.
 */
public final class CurrentAdmin {

    private CurrentAdmin() {
    }

    public static Optional<AdminPrincipal> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AdminPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    /** The current admin's display name, or null if the request wasn't made by an authenticated admin. */
    public static String nameOrNull() {
        return get().map(AdminPrincipal::name).orElse(null);
    }
}
