package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.request.CategoryRequest;
import com.fashion.Riskyc.dto.request.SubcategoryRequest;
import com.fashion.Riskyc.dto.response.CategoryResponse;
import com.fashion.Riskyc.dto.response.SubcategoryResponse;
import com.fashion.Riskyc.entity.Category;
import com.fashion.Riskyc.entity.Subcategory;
import com.fashion.Riskyc.exception.BadRequestException;
import com.fashion.Riskyc.exception.ConflictException;
import com.fashion.Riskyc.exception.ForbiddenException;
import com.fashion.Riskyc.exception.ResourceNotFoundException;
import com.fashion.Riskyc.repository.CategoryRepository;
import com.fashion.Riskyc.repository.EnterpriseRepository;
import com.fashion.Riskyc.repository.ProductRepository;
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
    private final ProductRepository productRepository;
    private final S3MediaService s3MediaService;
    private final TranslationService translationService;
    private final EnterpriseRepository enterpriseRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> listAll() {
        return categoryRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    /**
     * Top-level Categories stay a platform-curated list — a seller gets
     * assigned into existing ones at approval time (see
     * EnterpriseService#approve()), never creates a new department
     * themselves. Only Subcategory CRUD below is seller-reachable.
     */
    private void requirePlatformStaff() {
        if (CurrentAdmin.enterpriseIdOrNull() != null) {
            throw new ForbiddenException("Only platform staff can manage top-level categories.");
        }
    }

    /** A seller may only touch Subcategories under a Category their enterprise was assigned at approval time. */
    private void requireOwnsCategory(UUID categoryId) {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        if (scopeId == null) return;
        if (!enterpriseRepository.existsByIdAndCategories_Id(scopeId, categoryId)) {
            throw new ForbiddenException("This category isn't assigned to your enterprise.");
        }
    }

    public CategoryResponse create(CategoryRequest request) {
        requirePlatformStaff();
        if (categoryRepository.existsBySlug(request.slug())) {
            throw new BadRequestException("A category with slug '" + request.slug() + "' already exists");
        }
        if (categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw new BadRequestException("A category named '" + request.name() + "' already exists");
        }
        Category saved = categoryRepository.save(Category.builder()
                .slug(request.slug())
                .name(request.name())
                .nameFr(translationService.translateToFrench(request.name()))
                .icon(request.icon())
                .createdByName(CurrentAdmin.nameOrNull())
                .build());
        return toResponse(saved);
    }

    public CategoryResponse update(UUID id, CategoryRequest request) {
        requirePlatformStaff();
        Category category = getOrThrow(id);
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new BadRequestException("A category named '" + request.name() + "' already exists");
        }
        String previousName = category.getName();
        category.setSlug(request.slug());
        category.setName(request.name());
        category.setIcon(request.icon());
        if (!java.util.Objects.equals(previousName, request.name())) {
            category.setNameFr(translationService.translateToFrench(request.name()));
        }
        return toResponse(category);
    }

    public void delete(UUID id) {
        requirePlatformStaff();
        Category category = getOrThrow(id);
        if (productRepository.existsByCategoryId(id)) {
            throw new ConflictException(
                    "\"" + category.getName() + "\" still has products assigned to it. Delete those products first.");
        }
        long sellers = enterpriseRepository.countByCategoryId(id);
        if (sellers > 0) {
            throw new ConflictException("\"" + category.getName() + "\" is assigned to " + sellers
                    + (sellers == 1 ? " seller" : " sellers") + " — remove it from their categories first.");
        }
        if (category.getImageStorageKey() != null) {
            s3MediaService.delete(category.getImageStorageKey());
        }
        categoryRepository.delete(category);
    }

    public CategoryResponse uploadImage(UUID id, MultipartFile file) {
        requirePlatformStaff();
        Category category = getOrThrow(id);
        if (category.getImageStorageKey() != null) {
            s3MediaService.delete(category.getImageStorageKey());
        }
        String key = s3MediaService.upload(file, IMAGE_FOLDER + "/" + id);
        category.setImageStorageKey(key);
        return toResponse(category);
    }

    public CategoryResponse deleteImage(UUID id) {
        requirePlatformStaff();
        Category category = getOrThrow(id);
        if (category.getImageStorageKey() != null) {
            s3MediaService.delete(category.getImageStorageKey());
            category.setImageStorageKey(null);
        }
        return toResponse(category);
    }

    public SubcategoryResponse addSubcategory(UUID categoryId, SubcategoryRequest request) {
        requireOwnsCategory(categoryId);
        Category category = getOrThrow(categoryId);
        Subcategory sub = Subcategory.builder()
                .slug(request.slug())
                .name(request.name())
                .nameFr(translationService.translateToFrench(request.name()))
                .category(category)
                .build();
        category.getSubcategories().add(sub);
        subcategoryRepository.save(sub);
        return toResponse(sub);
    }

    public SubcategoryResponse updateSubcategory(UUID subcategoryId, SubcategoryRequest request) {
        Subcategory sub = subcategoryRepository.findById(subcategoryId)
                .orElseThrow(() -> ResourceNotFoundException.of("Subcategory", subcategoryId));
        requireOwnsCategory(sub.getCategory().getId());
        String previousName = sub.getName();
        sub.setSlug(request.slug());
        sub.setName(request.name());
        if (!java.util.Objects.equals(previousName, request.name())) {
            sub.setNameFr(translationService.translateToFrench(request.name()));
        }
        return toResponse(sub);
    }

    public void deleteSubcategory(UUID subcategoryId) {
        Subcategory sub = subcategoryRepository.findById(subcategoryId)
                .orElseThrow(() -> ResourceNotFoundException.of("Subcategory", subcategoryId));
        requireOwnsCategory(sub.getCategory().getId());
        if (productRepository.existsBySubcategoryId(subcategoryId)) {
            throw new ConflictException(
                    "\"" + sub.getName() + "\" still has products assigned to it. Delete those products first.");
        }
        subcategoryRepository.delete(sub);
    }

    private Category getOrThrow(UUID id) {
        return categoryRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Category", id));
    }

    private CategoryResponse toResponse(Category c) {
        String imageUrl = c.getImageStorageKey() != null
                ? s3MediaService.getPresignedUrl(c.getImageStorageKey())
                : null;
        return new CategoryResponse(
                c.getId(), c.getSlug(), c.getName(), c.getNameFr(), c.getIcon(), imageUrl, c.getCreatedByName(),
                c.getSubcategories().stream().map(this::toResponse).toList(),
                productRepository.countByCategoryIdAndHiddenFalse(c.getId())
        );
    }

    private SubcategoryResponse toResponse(Subcategory s) {
        return new SubcategoryResponse(
                s.getId(), s.getSlug(), s.getName(), s.getNameFr(),
                productRepository.countBySubcategoryIdAndHiddenFalse(s.getId())
        );
    }
}
