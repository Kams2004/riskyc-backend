package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.request.AdminUserRequest;
import com.fashion.Riskyc.dto.request.LoginRequest;
import com.fashion.Riskyc.dto.response.AdminLoginResponse;
import com.fashion.Riskyc.dto.response.AdminUserResponse;
import com.fashion.Riskyc.entity.AccountStatus;
import com.fashion.Riskyc.entity.AdminUser;
import com.fashion.Riskyc.entity.Role;
import com.fashion.Riskyc.exception.BadRequestException;
import com.fashion.Riskyc.exception.ResourceNotFoundException;
import com.fashion.Riskyc.repository.AdminUserRepository;
import com.fashion.Riskyc.repository.RoleRepository;
import com.fashion.Riskyc.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserService {

    private final AdminUserRepository adminUserRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listAll() {
        return adminUserRepository.findAll().stream().map(this::toResponse).toList();
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

        AdminUser user = adminUserRepository.saveAndFlush(AdminUser.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(role)
                .status(request.status() != null ? request.status() : AccountStatus.ACTIVE)
                .build());
        return toResponse(user);
    }

    public AdminUserResponse update(UUID id, AdminUserRequest request) {
        AdminUser user = getOrThrow(id);
        Role role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> ResourceNotFoundException.of("Role", request.roleId()));

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
        if (!adminUserRepository.existsById(id)) {
            throw ResourceNotFoundException.of("Admin user", id);
        }
        adminUserRepository.deleteById(id);
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
        user.setLastLogin(Instant.now());
        String token = jwtService.generateToken(user);
        return new AdminLoginResponse(token, toResponse(user), user.getRole().getPermissions());
    }

    private AdminUser getOrThrow(UUID id) {
        return adminUserRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Admin user", id));
    }

    private AdminUserResponse toResponse(AdminUser u) {
        return new AdminUserResponse(
                u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(),
                u.getRole() != null ? u.getRole().getId() : null,
                u.getRole() != null ? u.getRole().getName() : null,
                u.getStatus(), u.getCreatedAt(), u.getLastLogin()
        );
    }
}
