package com.fashion.Riskyc.controller;

import com.fashion.Riskyc.dto.request.GoogleAuthRequest;
import com.fashion.Riskyc.dto.request.LoginRequest;
import com.fashion.Riskyc.dto.request.RegisterCustomerRequest;
import com.fashion.Riskyc.dto.response.CustomerResponse;
import com.fashion.Riskyc.dto.response.ReferralSummaryResponse;
import com.fashion.Riskyc.entity.CustomerStatus;
import com.fashion.Riskyc.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping("/register")
    public ResponseEntity<CustomerResponse> register(@Valid @RequestBody RegisterCustomerRequest request) {
        return ResponseEntity.status(201).body(customerService.register(request));
    }

    @PostMapping("/login")
    public CustomerResponse login(@Valid @RequestBody LoginRequest request) {
        return customerService.login(request);
    }

    @PostMapping("/google")
    public CustomerResponse loginWithGoogle(@Valid @RequestBody GoogleAuthRequest request) {
        return customerService.loginWithGoogle(request);
    }

    @GetMapping("/{id}/referrals")
    public ReferralSummaryResponse referrals(@PathVariable UUID id) {
        return customerService.getReferralSummary(id);
    }

    @PatchMapping("/{id}/acronym")
    public CustomerResponse updateAcronym(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return customerService.updateAcronym(id, body.get("acronym"));
    }

    @GetMapping
    public List<CustomerResponse> list() {
        return customerService.listAll();
    }

    @PatchMapping("/{id}/status")
    public CustomerResponse updateStatus(@PathVariable UUID id, @RequestBody Map<String, CustomerStatus> body) {
        return customerService.updateStatus(id, body.get("status"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
