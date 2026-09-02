package com.fashion.Riskyc.controller;

import com.fashion.Riskyc.dto.request.DeliveryContactRequest;
import com.fashion.Riskyc.dto.response.DeliveryContactResponse;
import com.fashion.Riskyc.service.DeliveryContactService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Admin-managed list of delivery team members, attached automatically to packaging-confirmation messages — see ConversationService. */
@RestController
@RequestMapping("/api/delivery-contacts")
@RequiredArgsConstructor
public class DeliveryContactController {

    private final DeliveryContactService deliveryContactService;

    @GetMapping
    public List<DeliveryContactResponse> list() {
        return deliveryContactService.listAll();
    }

    @PostMapping
    public ResponseEntity<DeliveryContactResponse> create(@Valid @RequestBody DeliveryContactRequest request) {
        return ResponseEntity.status(201).body(deliveryContactService.create(request));
    }

    @PutMapping("/{id}")
    public DeliveryContactResponse update(@PathVariable UUID id, @Valid @RequestBody DeliveryContactRequest request) {
        return deliveryContactService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deliveryContactService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
