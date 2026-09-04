package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.request.RoleRequest;
import com.fashion.Riskyc.dto.response.RoleResponse;
import com.fashion.Riskyc.entity.Permission;
import com.fashion.Riskyc.entity.Role;
import com.fashion.Riskyc.exception.BadRequestException;
import com.fashion.Riskyc.exception.ResourceNotFoundException;
import com.fashion.Riskyc.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class RoleService {

    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public List<RoleResponse> listAll() {
        return roleRepository.findAll().stream().map(this::toResponse).toList();
    }

    public RoleResponse create(RoleRequest request) {
        if (roleRepository.existsByNameIgnoreCase(request.name())) {
            throw new BadRequestException("A role named '" + request.name() + "' already exists");
        }
        Role role = roleRepository.saveAndFlush(Role.builder()
                .name(request.name())
                .description(request.description())
                .permissions(normalize(request.permissions()))
                .build());
        return toResponse(role);
    }

    public RoleResponse update(UUID id, RoleRequest request) {
        Role role = getOrThrow(id);
        role.setName(request.name());
        role.setDescription(request.description());
        role.setPermissions(normalize(request.permissions()));
        return toResponse(role);
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
        // Treatment is a specialized view over orders (validated/packaging/
        // packaged) — the Treatment page fetches through the same GET
        // /api/orders every order list uses, just filtered client-side. A
        // Treatment-only role can claim and complete packaging but never
        // actually see anything to work on without VIEW_ORDERS too.
        if (permissions.contains(Permission.VIEW_TREATMENT)) {
            permissions.add(Permission.VIEW_ORDERS);
        }
        // Whoever can validate an order (MANAGE_ORDERS) or package it
        // (MANAGE_TREATMENT) should be able to send the "your order has
        // been packaged" confirmation without a separate, easy-to-forget
        // checkbox — SEND_PACKAGING_MESSAGE stays independently assignable
        // for a role that should only do that and nothing else.
        if (permissions.contains(Permission.MANAGE_ORDERS) || permissions.contains(Permission.MANAGE_TREATMENT)) {
            permissions.add(Permission.SEND_PACKAGING_MESSAGE);
        }
        return permissions;
    }

    public void delete(UUID id) {
        if (!roleRepository.existsById(id)) {
            throw ResourceNotFoundException.of("Role", id);
        }
        roleRepository.deleteById(id);
    }

    private Role getOrThrow(UUID id) {
        return roleRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Role", id));
    }

    private RoleResponse toResponse(Role r) {
        return new RoleResponse(r.getId(), r.getName(), r.getDescription(), r.getPermissions(), r.getCreatedAt());
    }
}
