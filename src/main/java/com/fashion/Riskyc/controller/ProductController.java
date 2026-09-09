package com.fashion.Riskyc.controller;

import com.fashion.Riskyc.dto.request.*;
import com.fashion.Riskyc.dto.response.MediaResponse;
import com.fashion.Riskyc.dto.response.ProductAuditLogResponse;
import com.fashion.Riskyc.dto.response.ProductResponse;
import com.fashion.Riskyc.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code GET /api/products} returns the whole page of products with every
 * image/video's presigned URL already resolved (see {@link ProductService}) —
 * the storefront never has to make a follow-up call per image.
 *
 * <p>Editing an existing product is split into section-scoped endpoints
 * ({@code /info}, {@code /pricing}, {@code /colors}, {@code /stock},
 * {@code /display}, {@code /visibility}, plus the existing media endpoints)
 * so a role can be granted the right to change just one part of a product —
 * see {@code SecurityConfig} for how each is gated. {@link #update} (the
 * legacy full-replace {@code PUT}) still exists for an admin with full
 * MANAGE_PRODUCTS access editing everything at once.
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public Page<ProductResponse> list(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String subcategory,
            @RequestParam(required = false) String q
    ) {
        return productService.list(pageable, category, subcategory, q);
    }

    @GetMapping("/admin")
    public List<ProductResponse> listForAdmin() {
        return productService.listAllForAdmin();
    }

    @GetMapping("/audit-log")
    public Page<ProductAuditLogResponse> allAuditLog(@PageableDefault(size = 50) Pageable pageable) {
        return productService.getAllAuditLog(pageable);
    }

    @GetMapping("/{id}/audit-log")
    public Page<ProductAuditLogResponse> auditLog(@PathVariable UUID id, @PageableDefault(size = 50) Pageable pageable) {
        return productService.getAuditLog(id, pageable);
    }

    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable UUID id) {
        return productService.getById(id);
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(201).body(productService.create(request));
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @PatchMapping("/{id}/info")
    public ProductResponse updateInfo(@PathVariable UUID id, @Valid @RequestBody ProductInfoRequest request) {
        return productService.updateInfo(id, request);
    }

    @PatchMapping("/{id}/pricing")
    public ProductResponse updatePricing(@PathVariable UUID id, @Valid @RequestBody ProductPricingRequest request) {
        return productService.updatePricing(id, request);
    }

    @PatchMapping("/{id}/colors")
    public ProductResponse updateColors(@PathVariable UUID id, @Valid @RequestBody ProductColorsRequest request) {
        return productService.updateColors(id, request);
    }

    @PatchMapping("/{id}/stock")
    public ProductResponse updateStock(@PathVariable UUID id, @Valid @RequestBody ProductStockRequest request) {
        return productService.updateStock(id, request);
    }

    @PatchMapping("/{id}/display")
    public ProductResponse updateDisplay(@PathVariable UUID id, @RequestBody ProductDisplayRequest request) {
        return productService.updateDisplay(id, request);
    }

    @PatchMapping("/{id}/visibility")
    public ProductResponse setHidden(@PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        boolean hidden = Boolean.TRUE.equals(body.get("hidden"));
        return productService.setHidden(id, hidden);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/media", consumes = "multipart/form-data")
    public ResponseEntity<MediaResponse> addMedia(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(201).body(productService.addMedia(id, file));
    }
}
