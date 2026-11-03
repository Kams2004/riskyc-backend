package com.fashion.Riskyc.controller;

import com.fashion.Riskyc.dto.request.EnterpriseApplicationRequest;
import com.fashion.Riskyc.dto.request.EnterpriseApprovalRequest;
import com.fashion.Riskyc.dto.request.EnterpriseRejectionRequest;
import com.fashion.Riskyc.dto.response.EnterpriseResponse;
import com.fashion.Riskyc.dto.response.EnterpriseStatusResponse;
import com.fashion.Riskyc.service.EnterpriseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/enterprises")
@RequiredArgsConstructor
public class EnterpriseController {

    private final EnterpriseService enterpriseService;

    @PostMapping("/apply")
    public ResponseEntity<EnterpriseResponse> apply(@Valid @RequestBody EnterpriseApplicationRequest request) {
        return ResponseEntity.status(201).body(enterpriseService.submitApplication(request));
    }

    @GetMapping
    public List<EnterpriseResponse> list(@RequestParam(required = false) Boolean pendingOnly) {
        return Boolean.TRUE.equals(pendingOnly) ? enterpriseService.listPending() : enterpriseService.listAll();
    }

    @GetMapping("/{id}")
    public EnterpriseResponse getById(@PathVariable UUID id) {
        return enterpriseService.getById(id);
    }

    @GetMapping("/{id}/status")
    public EnterpriseStatusResponse getPublicStatus(@PathVariable UUID id, @RequestParam String email) {
        return enterpriseService.getPublicStatus(id, email);
    }

    @PostMapping("/{id}/approve")
    public EnterpriseResponse approve(@PathVariable UUID id, @Valid @RequestBody EnterpriseApprovalRequest request) {
        return enterpriseService.approve(id, request);
    }

    @PostMapping("/{id}/reject")
    public EnterpriseResponse reject(@PathVariable UUID id, @RequestBody(required = false) EnterpriseRejectionRequest request) {
        return enterpriseService.reject(id, request != null ? request.reason() : null);
    }
}
