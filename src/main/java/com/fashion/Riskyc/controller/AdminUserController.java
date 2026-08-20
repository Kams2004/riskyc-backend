package com.fashion.Riskyc.controller;

import com.fashion.Riskyc.dto.request.AdminUserRequest;
import com.fashion.Riskyc.dto.request.LoginRequest;
import com.fashion.Riskyc.dto.response.AdminLoginResponse;
import com.fashion.Riskyc.dto.response.AdminUserResponse;
import com.fashion.Riskyc.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin-users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public List<AdminUserResponse> list() {
        return adminUserService.listAll();
    }

    @PostMapping
    public ResponseEntity<AdminUserResponse> create(@Valid @RequestBody AdminUserRequest request) {
        return ResponseEntity.status(201).body(adminUserService.create(request));
    }

    @PutMapping("/{id}")
    public AdminUserResponse update(@PathVariable UUID id, @Valid @RequestBody AdminUserRequest request) {
        return adminUserService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        adminUserService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public AdminLoginResponse login(@Valid @RequestBody LoginRequest request) {
        return adminUserService.login(request);
    }
}
