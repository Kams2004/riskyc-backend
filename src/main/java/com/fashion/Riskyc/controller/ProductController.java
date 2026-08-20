package com.fashion.Riskyc.controller;

import com.fashion.Riskyc.dto.request.ProductRequest;
import com.fashion.Riskyc.dto.response.MediaResponse;
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
