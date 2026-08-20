package com.fashion.Riskyc.controller;

import com.fashion.Riskyc.dto.request.CategoryRequest;
import com.fashion.Riskyc.dto.request.SubcategoryRequest;
import com.fashion.Riskyc.dto.response.CategoryResponse;
import com.fashion.Riskyc.dto.response.SubcategoryResponse;
import com.fashion.Riskyc.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public List<CategoryResponse> list() {
        return categoryService.listAll();
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(201).body(categoryService.create(request));
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/image")
    public CategoryResponse uploadImage(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return categoryService.uploadImage(id, file);
    }

    @DeleteMapping("/{id}/image")
    public CategoryResponse deleteImage(@PathVariable UUID id) {
        return categoryService.deleteImage(id);
    }

    @PostMapping("/{id}/subcategories")
    public ResponseEntity<SubcategoryResponse> addSubcategory(@PathVariable UUID id, @Valid @RequestBody SubcategoryRequest request) {
        return ResponseEntity.status(201).body(categoryService.addSubcategory(id, request));
    }

    @PutMapping("/subcategories/{subcategoryId}")
    public SubcategoryResponse updateSubcategory(@PathVariable UUID subcategoryId, @Valid @RequestBody SubcategoryRequest request) {
        return categoryService.updateSubcategory(subcategoryId, request);
    }

    @DeleteMapping("/subcategories/{subcategoryId}")
    public ResponseEntity<Void> deleteSubcategory(@PathVariable UUID subcategoryId) {
        categoryService.deleteSubcategory(subcategoryId);
        return ResponseEntity.noContent().build();
    }
}
