package com.fashion.Riskyc.security;

import com.fashion.Riskyc.entity.CustomerStatus;
import com.fashion.Riskyc.exception.ForbiddenException;
import com.fashion.Riskyc.exception.UnauthorizedException;
import com.fashion.Riskyc.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guards the endpoints that act on one customer's account (their orders, chat, referrals, display name). Those used
 * to trust whatever customer id was in the URL or body; now:
 * <ul>
 *   <li>a customer token must belong to that same customer (and the account must not be blocked);</li>
 *   <li>staff tokens are refused — the admin screens never act as a customer, and a seller's admin must not reach
 *       another shop's customer;</li>
 *   <li>a request with no token is refused once {@code app.security.customer-token-required} is on. It starts off so
 *       mobile app versions released before customer tokens keep working; switch it on once customers have updated.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CustomerAccess {

    private final CustomerRepository customerRepository;

    @Value("${app.security.customer-token-required:false}")
    private boolean tokenRequired;

    public void requireSelf(UUID customerId) {
        if (customerId == null) return;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth != null ? auth.getPrincipal() : null;

        if (principal instanceof CustomerPrincipal customer) {
            if (!customer.customerId().equals(customerId)) {
                throw new ForbiddenException("This belongs to another account.");
            }
            boolean blocked = customerRepository.findById(customerId)
                    .map(c -> c.getStatus() == CustomerStatus.BLOCKED)
                    .orElse(true);
            if (blocked) {
                throw new UnauthorizedException("This account can't be used any more. Please contact support.");
            }
            return;
        }
        if (principal instanceof AdminPrincipal || principal instanceof DevicePrincipal) {
            throw new ForbiddenException("This is a customer's own account.");
        }
        if (tokenRequired) {
            throw new UnauthorizedException("Please sign in again.");
        }
    }
}
