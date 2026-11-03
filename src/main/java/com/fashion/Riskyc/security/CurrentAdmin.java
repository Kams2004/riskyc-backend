package com.fashion.Riskyc.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

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

    /** The current admin's id, or null if the request wasn't made by an authenticated admin. */
    public static UUID idOrNull() {
        return get().map(p -> UUID.fromString(p.userId())).orElse(null);
    }

    /**
     * Null means "platform staff" (not scoped to any one enterprise — sees
     * and can act on everything, exactly today's behavior) OR "no
     * authenticated admin at all." Callers that need to tell those two
     * apart should check {@link #get()} directly; every scoping call site
     * added for the marketplace pivot treats both the same way (unscoped).
     */
    public static UUID enterpriseIdOrNull() {
        return get().map(AdminPrincipal::enterpriseId).filter(java.util.Objects::nonNull).map(UUID::fromString).orElse(null);
    }
}
