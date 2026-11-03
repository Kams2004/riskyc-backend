package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.request.RoleRequest;
import com.fashion.Riskyc.entity.Enterprise;
import com.fashion.Riskyc.entity.Permission;
import com.fashion.Riskyc.entity.Role;
import com.fashion.Riskyc.exception.ForbiddenException;
import com.fashion.Riskyc.repository.EnterpriseRepository;
import com.fashion.Riskyc.repository.RoleRepository;
import com.fashion.Riskyc.security.AdminPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Covers a gap found during the marketplace hardening review: a "Manage X"
 * permission carries no scope awareness of its own, and the platform's
 * seller-review/suspend endpoints (gated on VIEW_ENTERPRISES/
 * MANAGE_ENTERPRISES) have no per-call ownership guard of their own — so
 * without RoleService refusing to grant those to a scoped role, a seller's
 * own staff (who already has MANAGE_USERS, letting them edit their
 * enterprise's roles) could grant themselves a platform-only permission and
 * suspend a competitor or approve a fraudulent application.
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private EnterpriseRepository enterpriseRepository;

    @InjectMocks
    private RoleService roleService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void actAsScopedAdmin(UUID enterpriseId) {
        AdminPrincipal principal = new AdminPrincipal(
                UUID.randomUUID().toString(), "seller@example.com", "Seller Owner",
                UUID.randomUUID().toString(), "Owner", Set.of(), enterpriseId.toString());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null));
    }

    @Test
    void create_refusesToGrantAScopedRoleAPlatformOnlyPermission() {
        UUID enterpriseId = UUID.randomUUID();
        Enterprise enterprise = Enterprise.builder().id(enterpriseId).name("My Shop").build();
        when(enterpriseRepository.findById(enterpriseId)).thenReturn(Optional.of(enterprise));
        actAsScopedAdmin(enterpriseId);

        RoleRequest request = new RoleRequest("Sneaky Role", "desc", Set.of(Permission.VIEW_ORDERS, Permission.MANAGE_ENTERPRISES));

        assertThatThrownBy(() -> roleService.create(request)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void create_refusesViewEnterprisesTooNotJustManage() {
        UUID enterpriseId = UUID.randomUUID();
        Enterprise enterprise = Enterprise.builder().id(enterpriseId).name("My Shop").build();
        when(enterpriseRepository.findById(enterpriseId)).thenReturn(Optional.of(enterprise));
        actAsScopedAdmin(enterpriseId);

        RoleRequest request = new RoleRequest("Sneaky Role", "desc", Set.of(Permission.VIEW_ENTERPRISES));

        assertThatThrownBy(() -> roleService.create(request)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void create_platformAdminCanStillGrantPlatformOnlyPermissions() {
        // No security context — mirrors an unscoped platform admin's JWT.
        when(roleRepository.existsByNameIgnoreCaseAndEnterpriseIsNull("Sellers Reviewer")).thenReturn(false);
        when(roleRepository.saveAndFlush(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        RoleRequest request = new RoleRequest("Sellers Reviewer", "desc", Set.of(Permission.VIEW_ENTERPRISES, Permission.MANAGE_ENTERPRISES));

        assertThat(roleService.create(request).permissions()).contains(Permission.MANAGE_ENTERPRISES);
    }

    @Test
    void update_refusesToEscalateAnExistingScopedRoleToPlatformOnly() {
        UUID enterpriseId = UUID.randomUUID();
        Enterprise enterprise = Enterprise.builder().id(enterpriseId).name("My Shop").build();
        Role existingRole = Role.builder().id(UUID.randomUUID()).name("Owner").enterprise(enterprise).permissions(Set.of(Permission.VIEW_ORDERS)).build();
        when(roleRepository.findById(existingRole.getId())).thenReturn(Optional.of(existingRole));
        actAsScopedAdmin(enterpriseId);

        RoleRequest request = new RoleRequest("Owner", "desc", Set.of(Permission.VIEW_ORDERS, Permission.MANAGE_ENTERPRISES));

        assertThatThrownBy(() -> roleService.update(existingRole.getId(), request)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createScopedRole_refusesPlatformOnlyPermissionsEvenFromTheInternalEnterpriseApprovalCaller() {
        Enterprise enterprise = Enterprise.builder().id(UUID.randomUUID()).name("My Shop").build();

        assertThatThrownBy(() -> roleService.createScopedRole(enterprise, "Owner", Set.of(Permission.MANAGE_ENTERPRISES)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createScopedRole_allowsOrdinarySellerPermissions() {
        Enterprise enterprise = Enterprise.builder().id(UUID.randomUUID()).name("My Shop").build();
        when(roleRepository.saveAndFlush(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        Role role = roleService.createScopedRole(enterprise, "Owner", Set.of(Permission.VIEW_ORDERS, Permission.MANAGE_ORDERS));

        assertThat(role.getPermissions()).contains(Permission.VIEW_ORDERS, Permission.MANAGE_ORDERS);
        assertThat(role.getPermissions()).doesNotContain(Permission.MANAGE_ENTERPRISES, Permission.VIEW_ENTERPRISES);
    }
}
