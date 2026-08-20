package com.fashion.Riskyc.controller;

import com.fashion.Riskyc.dto.request.RoleRequest;
import com.fashion.Riskyc.dto.response.RoleResponse;
import com.fashion.Riskyc.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public List<RoleResponse> list() {
        return roleService.listAll();
    }

    @PostMapping
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody RoleRequest request) {
        return ResponseEntity.status(201).body(roleService.create(request));
    }

    @PutMapping("/{id}")
    public RoleResponse update(@PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
        return roleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        roleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
