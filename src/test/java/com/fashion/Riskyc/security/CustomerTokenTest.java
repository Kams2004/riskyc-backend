package com.fashion.Riskyc.security;

import com.fashion.Riskyc.config.JwtProperties;
import com.fashion.Riskyc.entity.AdminUser;
import com.fashion.Riskyc.entity.Customer;
import com.fashion.Riskyc.entity.CustomerStatus;
import com.fashion.Riskyc.entity.Permission;
import com.fashion.Riskyc.entity.Role;
import com.fashion.Riskyc.exception.ForbiddenException;
import com.fashion.Riskyc.exception.UnauthorizedException;
import com.fashion.Riskyc.repository.CustomerRepository;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerTokenTest {

    private final JwtService jwt = new JwtService(new JwtProperties());
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private String adminToken() {
        Role role = Role.builder().id(UUID.randomUUID()).name("Super Admin").permissions(Set.of(Permission.VIEW_ORDERS)).build();
        AdminUser admin = AdminUser.builder().id(UUID.randomUUID()).email("a@x.com").firstName("A").lastName("B").role(role).build();
        return jwt.generateToken(admin);
    }

    @Test
    void customerAndAdminTokensCannotBeSwapped() {
        String customerToken = jwt.generateCustomerToken(alice);
        assertEquals(alice, jwt.parseCustomer(customerToken));
        assertThrows(JwtException.class, () -> jwt.parse(customerToken), "a customer token must never pass as an admin's");

        String adminToken = adminToken();
        assertNotNull(jwt.parse(adminToken));
        assertThrows(JwtException.class, () -> jwt.parseCustomer(adminToken), "an admin token is not a customer session");
    }

    private CustomerAccess access(boolean tokenRequired, CustomerStatus status) {
        CustomerRepository repo = mock(CustomerRepository.class);
        Customer customer = new Customer();
        customer.setStatus(status);
        when(repo.findById(any())).thenReturn(Optional.of(customer));
        CustomerAccess access = new CustomerAccess(repo);
        ReflectionTestUtils.setField(access, "tokenRequired", tokenRequired);
        return access;
    }

    private void signIn(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Test
    void aCustomerReachesOnlyTheirOwnAccount() {
        CustomerAccess access = access(true, CustomerStatus.ACTIVE);
        signIn(new CustomerPrincipal(alice));
        assertDoesNotThrow(() -> access.requireSelf(alice));
        assertThrows(ForbiddenException.class, () -> access.requireSelf(bob));
    }

    @Test
    void aBlockedCustomersTokenStopsWorking() {
        CustomerAccess access = access(false, CustomerStatus.BLOCKED);
        signIn(new CustomerPrincipal(alice));
        assertThrows(UnauthorizedException.class, () -> access.requireSelf(alice));
    }

    @Test
    void noTokenIsAllowedOnlyWhileTheSwitchIsOff() {
        assertDoesNotThrow(() -> access(false, CustomerStatus.ACTIVE).requireSelf(alice), "older app versions keep working");
        assertThrows(UnauthorizedException.class, () -> access(true, CustomerStatus.ACTIVE).requireSelf(alice));
        assertDoesNotThrow(() -> access(true, CustomerStatus.ACTIVE).requireSelf(null), "guest checkout has no account");
    }

    @Test
    void staffTokensNeverActAsACustomer() {
        CustomerAccess access = access(false, CustomerStatus.ACTIVE);
        signIn(new AdminPrincipal("u", "a@x.com", "A", "r", "Role", Set.of(Permission.VIEW_ORDERS, Permission.VIEW_CUSTOMERS), null));
        // 403, not 401: a 401 would sign the admin out of the admin panel.
        assertThrows(ForbiddenException.class, () -> access.requireSelf(alice));
    }
}
