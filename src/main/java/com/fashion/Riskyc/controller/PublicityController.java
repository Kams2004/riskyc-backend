package com.fashion.Riskyc.controller;

import com.fashion.Riskyc.dto.request.ReorderPublicityRequest;
import com.fashion.Riskyc.dto.request.UpdatePublicityItemRequest;
import com.fashion.Riskyc.dto.response.PublicityItemResponse;
import com.fashion.Riskyc.service.PublicityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/publicity")
@RequiredArgsConstructor
public class PublicityController {

    private final PublicityService publicityService;

    /** Public — the mobile home screen's carousel. Active slides only. */
    @GetMapping
    public List<PublicityItemResponse> listActive() {
        return publicityService.listActive();
    }

    /** Admin — every slide, active or not, for management. */
    @GetMapping("/admin")
    public List<PublicityItemResponse> listAll() {
        return publicityService.listAll();
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<PublicityItemResponse> addItem(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String caption
    ) {
        return ResponseEntity.status(201).body(publicityService.addItem(file, caption));
    }

    @PatchMapping("/{id}")
    public PublicityItemResponse updateItem(@PathVariable UUID id, @RequestBody UpdatePublicityItemRequest request) {
        return publicityService.updateItem(id, request.caption(), request.active());
    }

    @PatchMapping("/reorder")
    public ResponseEntity<Void> reorder(@Valid @RequestBody ReorderPublicityRequest request) {
        publicityService.reorder(request.orderedIds());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        publicityService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
