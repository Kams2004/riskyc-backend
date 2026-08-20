package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.request.RoleRequest;
import com.fashion.Riskyc.dto.response.RoleResponse;
import com.fashion.Riskyc.entity.Role;
import com.fashion.Riskyc.exception.BadRequestException;
import com.fashion.Riskyc.exception.ResourceNotFoundException;
import com.fashion.Riskyc.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
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
                .permissions(request.permissions() != null ? new HashSet<>(request.permissions()) : new HashSet<>())
                .build());
        return toResponse(role);
    }

    public RoleResponse update(UUID id, RoleRequest request) {
        Role role = getOrThrow(id);
        role.setName(request.name());
        role.setDescription(request.description());
        role.setPermissions(request.permissions() != null ? new HashSet<>(request.permissions()) : new HashSet<>());
        return toResponse(role);
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
