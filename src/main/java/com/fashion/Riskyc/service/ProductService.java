package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.request.ProductColorRequest;
import com.fashion.Riskyc.dto.request.ProductRequest;
import com.fashion.Riskyc.dto.response.MediaResponse;
import com.fashion.Riskyc.dto.response.ProductColorResponse;
import com.fashion.Riskyc.dto.response.ProductResponse;
import com.fashion.Riskyc.entity.*;
import com.fashion.Riskyc.exception.BadRequestException;
import com.fashion.Riskyc.exception.ResourceNotFoundException;
import com.fashion.Riskyc.repository.CategoryRepository;
import com.fashion.Riskyc.repository.ProductMediaRepository;
import com.fashion.Riskyc.repository.ProductRepository;
import com.fashion.Riskyc.repository.SubcategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.fashion.Riskyc.security.CurrentAdmin;

import java.util.List;
import java.util.UUID;

/**
 * Owns the product catalog and, crucially, how product responses are built:
 * one query for the page of products (Hibernate batch-fetches the color and
 * media collections behind it — see {@code hibernate.default_batch_fetch_size}
 * in application.properties), then every media item's presigned URL is
 * resolved through {@link S3MediaService}'s cache. On a warm cache that's
 * ~1ms per image instead of the ~500ms a fresh presign costs, and unlike a
 * "fetch the URL separately per image" API shape, the client gets it all in
 * the single {@code GET /api/products} response.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private static final String MEDIA_FOLDER = "products";

    private final ProductRepository productRepository;
    private final ProductMediaRepository productMediaRepository;
    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final S3MediaService s3MediaService;

    @Transactional(readOnly = true)
    public Page<ProductResponse> list(Pageable pageable, String categorySlug, String subcategorySlug, String search) {
        Page<Product> page;
        if (search != null && !search.isBlank()) {
            page = productRepository.search(search.trim(), pageable);
        } else if (categorySlug != null && !categorySlug.isBlank() && subcategorySlug != null && !subcategorySlug.isBlank()) {
            page = productRepository.findByCategorySlugAndSubcategorySlugAndHiddenFalse(categorySlug, subcategorySlug, pageable);
        } else if (categorySlug != null && !categorySlug.isBlank()) {
            page = productRepository.findByCategorySlugAndHiddenFalse(categorySlug, pageable);
        } else {
            page = productRepository.findByHiddenFalse(pageable);
        }
        return page.map(this::toResponse);
    }

    /** Admin listing — includes hidden products, unlike {@link #list}. */
    @Transactional(readOnly = true)
    public List<ProductResponse> listAllForAdmin() {
        return productRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        return toResponse(getOrThrow(id));
    }

    public ProductResponse create(ProductRequest request) {
        Product product = new Product();
        product.setCreatedByName(CurrentAdmin.nameOrNull());
        applyRequest(product, request);
        // Flush so @CreationTimestamp/@UpdateTimestamp are populated before we
        // serialize the response (they're set by Hibernate at flush time).
        return toResponse(productRepository.saveAndFlush(product));
    }

    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = getOrThrow(id);
        applyRequest(product, request);
        return toResponse(product);
    }

    public void delete(UUID id) {
        Product product = getOrThrow(id);
        product.getMedia().forEach(m -> s3MediaService.delete(m.getStorageKey()));
        productRepository.delete(product);
    }

    public ProductResponse setHidden(UUID id, boolean hidden) {
        Product product = getOrThrow(id);
        product.setHidden(hidden);
        return toResponse(product);
    }

    public MediaResponse addMedia(UUID productId, MultipartFile file) {
        Product product = getOrThrow(productId);
        String key = s3MediaService.upload(file, MEDIA_FOLDER + "/" + productId);
        ProductMedia media = productMediaRepository.saveAndFlush(ProductMedia.builder()
                .storageKey(key)
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .sizeBytes(file.getSize())
                .type(isVideo(file.getContentType()) ? MediaType.VIDEO : MediaType.IMAGE)
                .product(product)
                .build());
        product.getMedia().add(media);
        return toMediaResponse(media);
    }

    public MediaResponse updateMediaPromoLabel(UUID mediaId, String text) {
        ProductMedia media = productMediaRepository.findById(mediaId)
                .orElseThrow(() -> ResourceNotFoundException.of("Media", mediaId));
        String trimmed = text == null ? null : text.trim();
        media.setPromoLabel(trimmed == null || trimmed.isEmpty() ? null : trimmed);
        return toMediaResponse(media);
    }

    public void deleteMedia(UUID mediaId) {
        ProductMedia media = productMediaRepository.findById(mediaId)
                .orElseThrow(() -> ResourceNotFoundException.of("Media", mediaId));
        s3MediaService.delete(media.getStorageKey());
        productMediaRepository.delete(media);
    }

    private void applyRequest(Product product, ProductRequest request) {
        Category category = categoryRepository.findBySlug(request.categorySlug())
                .orElseThrow(() -> new BadRequestException("Unknown category: " + request.categorySlug()));
        Subcategory subcategory = null;
        if (request.subcategorySlug() != null && !request.subcategorySlug().isBlank()) {
            subcategory = category.getSubcategories().stream()
                    .filter(s -> s.getSlug().equals(request.subcategorySlug()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException(
                            "Unknown subcategory '" + request.subcategorySlug() + "' for category '" + request.categorySlug() + "'"));
        }

        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setOriginalPrice(request.originalPrice());
        product.setCategory(category);
        product.setSubcategory(subcategory);
        product.setSizes(request.sizes() != null ? request.sizes() : List.of());
        product.setTags(request.tags() != null ? request.tags() : List.of());
        product.setBadge(request.badge());
        if (request.hidden() != null) {
            product.setHidden(request.hidden());
        }
        if (request.rating() != null) {
            product.setRating(request.rating());
        }
        if (request.reviews() != null) {
            product.setReviews(request.reviews());
        }

        product.getColors().clear();
        for (ProductColorRequest colorReq : request.colors()) {
            product.getColors().add(ProductColor.builder()
                    .name(colorReq.name())
                    .hex(colorReq.hex())
                    .stock(colorReq.stock())
                    .product(product)
                    .build());
        }
    }

    private Product getOrThrow(UUID id) {
        return productRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Product", id));
    }

    private boolean isVideo(String contentType) {
        return contentType != null && contentType.startsWith("video/");
    }

    private ProductResponse toResponse(Product p) {
        List<ProductColorResponse> colors = p.getColors().stream()
                .map(c -> new ProductColorResponse(c.getId(), c.getName(), c.getHex(), c.getStock()))
                .toList();
        List<MediaResponse> media = p.getMedia().stream().map(this::toMediaResponse).toList();

        return new ProductResponse(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getPrice(),
                p.getOriginalPrice(),
                p.getCategory() != null ? p.getCategory().getSlug() : null,
                p.getSubcategory() != null ? p.getSubcategory().getSlug() : null,
                // copy (not pass-through) so the lazy Hibernate collection is
                // fully materialized here, inside the transaction, instead of
                // leaking an uninitialized proxy into the DTO
                List.copyOf(p.getSizes()),
                List.copyOf(p.getTags()),
                p.getRating(),
                p.getReviews(),
                p.getBadge(),
                p.isHidden(),
                colors,
                media,
                p.getCreatedByName(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    private MediaResponse toMediaResponse(ProductMedia m) {
        return new MediaResponse(
                m.getId(),
                s3MediaService.getPresignedUrl(m.getStorageKey()),
                m.getType(),
                m.getContentType(),
                m.getSizeBytes(),
                m.getPromoLabel(),
                m.getUploadedAt()
        );
    }
}
