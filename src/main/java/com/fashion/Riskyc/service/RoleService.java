package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.request.RoleRequest;
import com.fashion.Riskyc.dto.response.RoleResponse;
import com.fashion.Riskyc.entity.AdminUser;
import com.fashion.Riskyc.entity.Enterprise;
import com.fashion.Riskyc.entity.Permission;
import com.fashion.Riskyc.entity.Role;
import com.fashion.Riskyc.exception.BadRequestException;
import com.fashion.Riskyc.exception.ConflictException;
import com.fashion.Riskyc.exception.ForbiddenException;
import com.fashion.Riskyc.exception.ResourceNotFoundException;
import com.fashion.Riskyc.repository.AdminUserRepository;
import com.fashion.Riskyc.repository.EnterpriseRepository;
import com.fashion.Riskyc.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fashion.Riskyc.security.CurrentAdmin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class RoleService {

    private final RoleRepository roleRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final AdminUserRepository adminUserRepository;

    /**
     * Permissions whose endpoints are platform-wide with no per-seller scoping (seller review, every customer,
     * every chat, homepage publicity) — a seller's role can never be granted them, and effectivePermissions()
     * strips them at token issue time in case an older seller role still holds one.
     */
    public static final Set<Permission> PLATFORM_ONLY_PERMISSIONS = Set.of(
            Permission.VIEW_ENTERPRISES, Permission.MANAGE_ENTERPRISES,
            Permission.VIEW_CUSTOMERS, Permission.MANAGE_CUSTOMERS,
            Permission.VIEW_CHAT, Permission.MANAGE_CHAT,
            Permission.VIEW_PUBLICITY, Permission.MANAGE_PUBLICITY);

    /** What an admin can actually do: their role's permissions, minus platform-only ones for a seller's staff. */
    public static Set<Permission> effectivePermissions(AdminUser user) {
        Set<Permission> permissions = new HashSet<>(user.getRole().getPermissions());
        if (user.getEnterprise() != null) permissions.removeAll(PLATFORM_ONLY_PERMISSIONS);
        return permissions;
    }

    /** Platform staff see every platform-wide role (never another enterprise's); a scoped admin sees only their own enterprise's roles. */
    @Transactional(readOnly = true)
    public List<RoleResponse> listAll() {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        List<Role> roles = scopeId == null ? roleRepository.findByEnterpriseIsNull() : roleRepository.findByEnterpriseId(scopeId);
        return roles.stream().map(this::toResponse).toList();
    }

    public RoleResponse create(RoleRequest request) {
        Enterprise scope = resolveCreatorEnterprise();
        if (duplicateName(request.name(), scope)) {
            throw new BadRequestException("A role named '" + request.name() + "' already exists");
        }
        Set<Permission> permissions = normalize(request.permissions());
        requireNoPlatformOnlyPermissionsWhenScoped(scope, permissions);
        Role role = roleRepository.saveAndFlush(Role.builder()
                .name(request.name())
                .description(request.description())
                .permissions(permissions)
                .enterprise(scope)
                .build());
        return toResponse(role);
    }

    public RoleResponse update(UUID id, RoleRequest request) {
        Role role = getOrThrow(id);
        requireOwnedOrPlatform(role);
        if (!role.getName().equalsIgnoreCase(request.name()) && duplicateName(request.name(), role.getEnterprise())) {
            throw new BadRequestException("A role named '" + request.name() + "' already exists");
        }
        Set<Permission> permissions = normalize(request.permissions());
        requireNoPlatformOnlyPermissionsWhenScoped(role.getEnterprise(), permissions);
        role.setName(request.name());
        role.setDescription(request.description());
        role.setPermissions(permissions);
        return toResponse(role);
    }

    /**
     * A seller's own staff can edit their enterprise's roles (MANAGE_USERS
     * covers that), but a "Manage X" permission carries no scope awareness
     * of its own — only the explicit requireOwnedOrPlatform()-style guards
     * each service applies do. VIEW_ENTERPRISES/MANAGE_ENTERPRISES gate the
     * platform's own seller-review/suspend endpoints, which have no such
     * per-call ownership guard (there's no "owning enterprise" of another
     * enterprise's review to check) — so without this, a scoped admin could
     * grant their own role a platform-only permission and, say, suspend a
     * competitor or approve a fraudulent application. Checked against every
     * write path that sets a role's permissions, including the one
     * EnterpriseService#approve() itself uses to provision a new seller's
     * first role (defense in depth — DEFAULT_OWNER_PERMISSIONS there is
     * already a safe, hardcoded set, so this should never actually fire for
     * that caller).
     */
    private void requireNoPlatformOnlyPermissionsWhenScoped(Enterprise scope, Set<Permission> permissions) {
        if (scope == null) return;
        if (!java.util.Collections.disjoint(permissions, PLATFORM_ONLY_PERMISSIONS)) {
            throw new ForbiddenException("A seller's role can't be granted platform-only permissions.");
        }
    }

    private Enterprise resolveCreatorEnterprise() {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        return scopeId == null ? null : enterpriseRepository.findById(scopeId)
                .orElseThrow(() -> ResourceNotFoundException.of("Enterprise", scopeId));
    }

    private boolean duplicateName(String name, Enterprise scope) {
        return scope == null
                ? roleRepository.existsByNameIgnoreCaseAndEnterpriseIsNull(name)
                : roleRepository.existsByNameIgnoreCaseAndEnterpriseId(name, scope.getId());
    }

    private void requireOwnedOrPlatform(Role role) {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        if (scopeId == null) return;
        if (role.getEnterprise() == null || !scopeId.equals(role.getEnterprise().getId())) {
            throw new ForbiddenException("This role belongs to a different seller.");
        }
    }

    /**
     * A "Manage X" permission is useless on its own — every page and API read
     * it needs (the order list, the products grid, ...) is gated on "View X",
     * so an admin who only ticks "Manage Orders" in the role editor ends up
     * with a role that can't see anything to manage. Rather than trust every
     * caller (this editor, future ones, DataSeeder) to remember the pairing,
     * every MANAGE_X permission implies its VIEW_X counterpart here, once.
     */
    private static Set<Permission> normalize(Set<Permission> requested) {
        Set<Permission> permissions = requested != null ? new HashSet<>(requested) : new HashSet<>();
        for (Permission p : new HashSet<>(permissions)) {
            if (p.name().startsWith("MANAGE_")) {
                try {
                    permissions.add(Permission.valueOf("VIEW_" + p.name().substring("MANAGE_".length())));
                } catch (IllegalArgumentException ignored) {
                    // No matching VIEW_ counterpart for this permission — nothing to imply.
                }
            }
        }
        // Whoever can validate an order (MANAGE_ORDERS) or package it
        // (MANAGE_TREATMENT) should be able to send the "your order has
        // been packaged" confirmation without a separate, easy-to-forget
        // checkbox — SEND_PACKAGING_MESSAGE stays independently assignable
        // for a role that should only do that and nothing else.
        if (permissions.contains(Permission.MANAGE_ORDERS) || permissions.contains(Permission.MANAGE_TREATMENT)) {
            permissions.add(Permission.SEND_PACKAGING_MESSAGE);
        }
        // MANAGE_PRODUCTS is the "full access" product permission — it implies
        // every section-scoped one below it, so existing roles that already
        // have it keep working exactly as before with no reconfiguration.
        if (permissions.contains(Permission.MANAGE_PRODUCTS)) {
            permissions.addAll(Set.of(
                    Permission.CREATE_PRODUCT, Permission.DELETE_PRODUCT,
                    Permission.UPDATE_PRODUCT_INFO, Permission.UPDATE_PRODUCT_PRICING, Permission.UPDATE_PRODUCT_IMAGES,
                    Permission.UPDATE_PRODUCT_COLORS, Permission.UPDATE_PRODUCT_STOCK,
                    Permission.UPDATE_PRODUCT_DISPLAY, Permission.UPDATE_PRODUCT_VISIBILITY));
        }
        // Any of the section-scoped product permissions is useless without
        // being able to see the product to act on it in the first place.
        Set<Permission> productSectionPermissions = Set.of(
                Permission.CREATE_PRODUCT, Permission.DELETE_PRODUCT,
                Permission.UPDATE_PRODUCT_INFO, Permission.UPDATE_PRODUCT_PRICING, Permission.UPDATE_PRODUCT_IMAGES,
                Permission.UPDATE_PRODUCT_COLORS, Permission.UPDATE_PRODUCT_STOCK,
                Permission.UPDATE_PRODUCT_DISPLAY, Permission.UPDATE_PRODUCT_VISIBILITY);
        if (!java.util.Collections.disjoint(permissions, productSectionPermissions)) {
            permissions.add(Permission.VIEW_PRODUCTS);
        }
        return permissions;
    }

    public void delete(UUID id) {
        Role role = getOrThrow(id);
        requireOwnedOrPlatform(role);
        long assigned = adminUserRepository.countByRoleId(id);
        if (assigned > 0) {
            throw new ConflictException("This role is still assigned to " + assigned + (assigned == 1 ? " user" : " users")
                    + " — give them another role first.");
        }
        roleRepository.deleteById(id);
    }

    /**
     * Shared with {@code EnterpriseService.approve()} so a newly-approved seller's
     * first ("Owner") role is created through the exact same path — and the same
     * {@link #normalize} VIEW/MANAGE pairing — as a role created through the UI.
     */
    public Role createScopedRole(Enterprise enterprise, String name, Set<Permission> permissions) {
        Set<Permission> normalized = normalize(permissions);
        requireNoPlatformOnlyPermissionsWhenScoped(enterprise, normalized);
        return roleRepository.saveAndFlush(Role.builder()
                .name(name)
                .description("Default role created when this seller's account was approved.")
                .permissions(normalized)
                .enterprise(enterprise)
                .build());
    }

    private Role getOrThrow(UUID id) {
        return roleRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Role", id));
    }

    private RoleResponse toResponse(Role r) {
        return new RoleResponse(r.getId(), r.getName(), r.getDescription(), r.getPermissions(), r.getCreatedAt(),
                r.getEnterprise() != null ? r.getEnterprise().getId() : null);
    }
}
