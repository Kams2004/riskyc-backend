package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.request.CategoryRequest;
import com.fashion.Riskyc.dto.request.SubcategoryRequest;
import com.fashion.Riskyc.dto.response.CategoryResponse;
import com.fashion.Riskyc.dto.response.SubcategoryResponse;
import com.fashion.Riskyc.entity.Category;
import com.fashion.Riskyc.entity.Subcategory;
import com.fashion.Riskyc.exception.BadRequestException;
import com.fashion.Riskyc.exception.ResourceNotFoundException;
import com.fashion.Riskyc.repository.CategoryRepository;
import com.fashion.Riskyc.repository.SubcategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.fashion.Riskyc.security.CurrentAdmin;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private static final String IMAGE_FOLDER = "categories";

    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final S3MediaService s3MediaService;

    @Transactional(readOnly = true)
    public List<CategoryResponse> listAll() {
        return categoryRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsBySlug(request.slug())) {
            throw new BadRequestException("A category with slug '" + request.slug() + "' already exists");
        }
        Category saved = categoryRepository.save(Category.builder()
                .slug(request.slug())
                .name(request.name())
                .icon(request.icon())
                .createdByName(CurrentAdmin.nameOrNull())
                .build());
        return toResponse(saved);
    }

    public CategoryResponse update(UUID id, CategoryRequest request) {
        Category category = getOrThrow(id);
        category.setSlug(request.slug());
        category.setName(request.name());
        category.setIcon(request.icon());
        return toResponse(category);
    }

    public void delete(UUID id) {
        Category category = getOrThrow(id);
        if (category.getImageStorageKey() != null) {
            s3MediaService.delete(category.getImageStorageKey());
        }
        categoryRepository.delete(category);
    }

    public CategoryResponse uploadImage(UUID id, MultipartFile file) {
        Category category = getOrThrow(id);
        if (category.getImageStorageKey() != null) {
            s3MediaService.delete(category.getImageStorageKey());
        }
        String key = s3MediaService.upload(file, IMAGE_FOLDER + "/" + id);
        category.setImageStorageKey(key);
        return toResponse(category);
    }

    public CategoryResponse deleteImage(UUID id) {
        Category category = getOrThrow(id);
        if (category.getImageStorageKey() != null) {
            s3MediaService.delete(category.getImageStorageKey());
            category.setImageStorageKey(null);
        }
        return toResponse(category);
    }

    public SubcategoryResponse addSubcategory(UUID categoryId, SubcategoryRequest request) {
        Category category = getOrThrow(categoryId);
        Subcategory sub = Subcategory.builder()
                .slug(request.slug())
                .name(request.name())
                .category(category)
                .build();
        category.getSubcategories().add(sub);
        subcategoryRepository.save(sub);
        return toResponse(sub);
    }

    public SubcategoryResponse updateSubcategory(UUID subcategoryId, SubcategoryRequest request) {
        Subcategory sub = subcategoryRepository.findById(subcategoryId)
                .orElseThrow(() -> ResourceNotFoundException.of("Subcategory", subcategoryId));
        sub.setSlug(request.slug());
        sub.setName(request.name());
        return toResponse(sub);
    }

    public void deleteSubcategory(UUID subcategoryId) {
        if (!subcategoryRepository.existsById(subcategoryId)) {
            throw ResourceNotFoundException.of("Subcategory", subcategoryId);
        }
        subcategoryRepository.deleteById(subcategoryId);
    }

    private Category getOrThrow(UUID id) {
        return categoryRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Category", id));
    }

    private CategoryResponse toResponse(Category c) {
        String imageUrl = c.getImageStorageKey() != null
                ? s3MediaService.getPresignedUrl(c.getImageStorageKey())
                : null;
        return new CategoryResponse(
                c.getId(), c.getSlug(), c.getName(), c.getIcon(), imageUrl, c.getCreatedByName(),
                c.getSubcategories().stream().map(this::toResponse).toList()
        );
    }

    private SubcategoryResponse toResponse(Subcategory s) {
        return new SubcategoryResponse(s.getId(), s.getSlug(), s.getName());
    }
}
