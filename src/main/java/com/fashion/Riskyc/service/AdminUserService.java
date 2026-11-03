package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.request.AdminUserRequest;
import com.fashion.Riskyc.dto.request.ChangePasswordRequest;
import com.fashion.Riskyc.dto.request.LoginRequest;
import com.fashion.Riskyc.dto.response.AdminLoginResponse;
import com.fashion.Riskyc.dto.response.AdminUserResponse;
import com.fashion.Riskyc.entity.AccountStatus;
import com.fashion.Riskyc.entity.AdminUser;
import com.fashion.Riskyc.entity.Enterprise;
import com.fashion.Riskyc.entity.EnterpriseStatus;
import com.fashion.Riskyc.entity.Role;
import com.fashion.Riskyc.exception.BadRequestException;
import com.fashion.Riskyc.exception.ForbiddenException;
import com.fashion.Riskyc.exception.ResourceNotFoundException;
import com.fashion.Riskyc.repository.AdminUserRepository;
import com.fashion.Riskyc.repository.EnterpriseRepository;
import com.fashion.Riskyc.repository.RoleRepository;
import com.fashion.Riskyc.security.CurrentAdmin;
import com.fashion.Riskyc.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserService {

    private final AdminUserRepository adminUserRepository;
    private final RoleRepository roleRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** Platform staff see every admin; a scoped admin sees only their own enterprise's staff. */
    @Transactional(readOnly = true)
    public List<AdminUserResponse> listAll() {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        List<AdminUser> users = scopeId == null ? adminUserRepository.findAll() : adminUserRepository.findByEnterpriseId(scopeId);
        return users.stream().map(this::toResponse).toList();
    }

    public AdminUserResponse create(AdminUserRequest request) {
        if (adminUserRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BadRequestException("An admin user with this email already exists");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new BadRequestException("Password is required");
        }
        Role role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> ResourceNotFoundException.of("Role", request.roleId()));
        Enterprise enterprise = resolveCreatorEnterprise();
        requireRoleMatchesScope(role, enterprise);

        AdminUser user = adminUserRepository.saveAndFlush(AdminUser.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(role)
                .enterprise(enterprise)
                .status(request.status() != null ? request.status() : AccountStatus.ACTIVE)
                .build());
        return toResponse(user);
    }

    /** A scoped admin's new staff always belongs to their own enterprise. Platform staff (no scope) create another platform admin, exactly as before this field existed. */
    private Enterprise resolveCreatorEnterprise() {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        return scopeId == null ? null : enterpriseRepository.findById(scopeId)
                .orElseThrow(() -> ResourceNotFoundException.of("Enterprise", scopeId));
    }

    /** Stops a seller handing their staff a platform-wide role, or a platform admin handing someone a role scoped to a different enterprise. */
    private void requireRoleMatchesScope(Role role, Enterprise targetEnterprise) {
        UUID targetId = targetEnterprise != null ? targetEnterprise.getId() : null;
        UUID roleEnterpriseId = role.getEnterprise() != null ? role.getEnterprise().getId() : null;
        if (!Objects.equals(targetId, roleEnterpriseId)) {
            throw new BadRequestException("That role isn't available for this account.");
        }
    }

    public AdminUserResponse update(UUID id, AdminUserRequest request) {
        AdminUser user = getOrThrow(id);
        requireOwnedOrPlatform(user);
        Role role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> ResourceNotFoundException.of("Role", request.roleId()));
        requireRoleMatchesScope(role, user.getEnterprise());

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());
        user.setRole(role);
        if (request.status() != null) {
            user.setStatus(request.status());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return toResponse(user);
    }

    public void delete(UUID id) {
        AdminUser user = getOrThrow(id);
        requireOwnedOrPlatform(user);
        // A logged-in admin can update their own profile freely, but deleting
        // it here — while still authenticated as it — would either orphan
        // their own session or (worse, if they're the only admin left) lock
        // everyone out until someone reseeds the database by hand. Force
        // that decision through another admin account instead.
        if (id.equals(CurrentAdmin.idOrNull())) {
            throw new BadRequestException("You cannot delete your own account. Ask another admin to remove it if needed.");
        }
        adminUserRepository.deleteById(id);
    }

    /** Self-service — any authenticated admin changes their own password, e.g. after the forced-change prompt on an invite-provisioned account. */
    public void changeOwnPassword(ChangePasswordRequest request) {
        UUID id = CurrentAdmin.idOrNull();
        if (id == null) {
            throw new BadRequestException("Not authenticated");
        }
        AdminUser user = getOrThrow(id);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
    }

    private void requireOwnedOrPlatform(AdminUser user) {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        if (scopeId == null) return;
        if (user.getEnterprise() == null || !scopeId.equals(user.getEnterprise().getId())) {
            throw new ForbiddenException("This account belongs to a different seller.");
        }
    }

    public AdminLoginResponse login(LoginRequest request) {
        AdminUser user = adminUserRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new BadRequestException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadRequestException("Invalid email or password");
        }
        if (user.getStatus() == AccountStatus.INACTIVE) {
            throw new BadRequestException("This account is inactive. Please contact an administrator.");
        }
        if (user.getEnterprise() != null && user.getEnterprise().getStatus() != EnterpriseStatus.ACTIVE) {
            throw new BadRequestException("This seller account is currently suspended. Please contact the platform.");
        }
        user.setLastLogin(Instant.now());
        String token = jwtService.generateToken(user);
        Set<UUID> categoryIds = user.getEnterprise() != null
                ? user.getEnterprise().getCategories().stream().map(c -> c.getId()).collect(Collectors.toUnmodifiableSet())
                : Set.of();
        return new AdminLoginResponse(token, toResponse(user), user.getRole().getPermissions(), user.isMustChangePassword(), categoryIds);
    }

    private AdminUser getOrThrow(UUID id) {
        return adminUserRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Admin user", id));
    }

    private AdminUserResponse toResponse(AdminUser u) {
        return new AdminUserResponse(
                u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(),
                u.getRole() != null ? u.getRole().getId() : null,
                u.getRole() != null ? u.getRole().getName() : null,
                u.getStatus(), u.getCreatedAt(), u.getLastLogin(),
                u.getEnterprise() != null ? u.getEnterprise().getId() : null
        );
    }
}
