package com.fashion.Riskyc.controller;

import com.fashion.Riskyc.dto.request.CreateOrderRequest;
import com.fashion.Riskyc.dto.request.SetPaymentMethodRequest;
import com.fashion.Riskyc.dto.request.UpdateOrderStatusRequest;
import com.fashion.Riskyc.dto.response.OrderResponse;
import com.fashion.Riskyc.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public List<OrderResponse> list() {
        return orderService.listAll();
    }

    @GetMapping("/customer/{customerId}")
    public List<OrderResponse> listForCustomer(@PathVariable UUID customerId) {
        return orderService.listForCustomer(customerId);
    }

    @GetMapping("/{id}")
    public OrderResponse getById(@PathVariable UUID id) {
        return orderService.getById(id);
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(201).body(orderService.create(request));
    }

    @PostMapping("/{id}/payment-method")
    public OrderResponse setPaymentMethod(@PathVariable UUID id, @Valid @RequestBody SetPaymentMethodRequest request) {
        return orderService.setPaymentMethod(id, request.paymentMethod());
    }

    @PostMapping(value = "/{id}/payment-proof", consumes = "multipart/form-data")
    public OrderResponse uploadPaymentProof(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return orderService.uploadPaymentProof(id, file);
    }

    @PatchMapping("/{id}/status")
    public OrderResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderService.updateStatus(id, request.status());
    }

    @PatchMapping("/{id}/packaging/start")
    public OrderResponse startPackaging(@PathVariable UUID id) {
        return orderService.startPackaging(id);
    }

    @PatchMapping("/{id}/packaging/complete")
    public OrderResponse completePackaging(@PathVariable UUID id) {
        return orderService.completePackaging(id);
    }
}
