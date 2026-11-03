package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.request.*;
import com.fashion.Riskyc.dto.response.BulkPriceTierResponse;
import com.fashion.Riskyc.dto.response.MediaResponse;
import com.fashion.Riskyc.dto.response.ProductAuditLogResponse;
import com.fashion.Riskyc.dto.response.ProductColorResponse;
import com.fashion.Riskyc.dto.response.ProductResponse;
import com.fashion.Riskyc.dto.response.ProductVariantResponse;
import com.fashion.Riskyc.entity.*;
import com.fashion.Riskyc.exception.BadRequestException;
import com.fashion.Riskyc.exception.ForbiddenException;
import com.fashion.Riskyc.exception.ResourceNotFoundException;
import com.fashion.Riskyc.repository.CategoryRepository;
import com.fashion.Riskyc.repository.EnterpriseRepository;
import com.fashion.Riskyc.repository.OrderItemRepository;
import com.fashion.Riskyc.repository.ProductAuditLogRepository;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final ProductAuditLogRepository productAuditLogRepository;
    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final OrderItemRepository orderItemRepository;
    private final S3MediaService s3MediaService;
    private final TranslationService translationService;
    private final ProductVariantService productVariantService;
    private final EnterpriseRepository enterpriseRepository;

    @Transactional(readOnly = true)
    public Page<ProductResponse> list(Pageable pageable, String categorySlug, String subcategorySlug, String search) {
        Page<Product> page;
        if (search != null && !search.isBlank()) {
            page = productRepository.search(search.trim(), EnterpriseStatus.SUSPENDED, pageable);
        } else if (categorySlug != null && !categorySlug.isBlank() && subcategorySlug != null && !subcategorySlug.isBlank()) {
            page = productRepository.findByCategorySlugAndSubcategorySlugAndHiddenFalseAndEnterprise_StatusNot(
                    categorySlug, subcategorySlug, EnterpriseStatus.SUSPENDED, pageable);
        } else if (categorySlug != null && !categorySlug.isBlank()) {
            page = productRepository.findByCategorySlugAndHiddenFalseAndEnterprise_StatusNot(categorySlug, EnterpriseStatus.SUSPENDED, pageable);
        } else {
            page = productRepository.findByHiddenFalseAndEnterprise_StatusNot(EnterpriseStatus.SUSPENDED, pageable);
        }
        return page.map(this::toResponse);
    }

    /**
     * Admin listing — includes hidden products, unlike {@link #list}.
     * Platform staff (no enterprise scope) see everything, exactly as
     * before; a scoped admin sees only their own enterprise's products.
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> listAllForAdmin() {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        List<Product> products = scopeId == null ? productRepository.findAll() : productRepository.findByEnterpriseId(scopeId);
        return products.stream().map(this::toResponse).toList();
    }

    /**
     * The POS catalog snapshot — every sellable (non-hidden) product with
     * its variants, unpaginated so a till can cache the whole thing
     * locally. A scoped admin's till only ever offers their own
     * enterprise's products — selling another seller's inventory isn't a
     * read-visibility nuance like the storefront's hidden/suspended
     * filtering, it's a different seller's stock entirely.
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> listForPos() {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        List<Product> products = scopeId == null
                ? productRepository.findByHiddenFalseAndEnterprise_StatusNot(EnterpriseStatus.SUSPENDED)
                : productRepository.findByEnterpriseIdAndHiddenFalse(scopeId);
        return products.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        return toResponse(getOrThrow(id));
    }

    public ProductResponse create(ProductRequest request) {
        Product product = new Product();
        product.setCreatedByName(CurrentAdmin.nameOrNull());
        product.setEnterprise(resolveCreatorEnterprise());
        applyRequest(product, request);
        // Flush so @CreationTimestamp/@UpdateTimestamp are populated before we
        // serialize the response (they're set by Hibernate at flush time).
        Product saved = productRepository.saveAndFlush(product);
        logAudit(saved, ProductAuditSection.CREATED, "Created the product");
        return toResponse(saved);
    }

    /**
     * A scoped admin's new product always belongs to their own enterprise —
     * never client-chosen. Platform staff (no scope) default to the
     * platform's own catalog, same as before this field existed.
     */
    private Enterprise resolveCreatorEnterprise() {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        if (scopeId != null) {
            return enterpriseRepository.findById(scopeId).orElseThrow(() -> ResourceNotFoundException.of("Enterprise", scopeId));
        }
        return enterpriseRepository.findByPlatformDefaultTrue().orElseThrow(() -> new BadRequestException("No platform-default enterprise seeded"));
    }

    /**
     * Throws when the acting admin is scoped to an enterprise and this
     * product isn't theirs. A platform admin (no scope) always passes —
     * this is also the mechanism that keeps UPDATE_PRODUCT_VISIBILITY
     * working across every enterprise's products for platform oversight
     * (Phase 10), since the guard simply never fires for them.
     */
    private void requireOwnedOrPlatform(Product product) {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        if (scopeId == null) return;
        if (product.getEnterprise() == null || !scopeId.equals(product.getEnterprise().getId())) {
            throw new ForbiddenException("This product belongs to a different seller.");
        }
    }

    /** Legacy full-replace — kept for a full MANAGE_PRODUCTS admin submitting every section at once. */
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = getOrThrow(id);
        requireOwnedOrPlatform(product);
        applyRequest(product, request);
        logAudit(product, ProductAuditSection.INFO, "Updated the product (full save)");
        return toResponse(product);
    }

    public void delete(UUID id) {
        Product product = getOrThrow(id);
        requireOwnedOrPlatform(product);
        // Past orders keep their own name/price snapshot on each line item, so
        // detaching (not deleting) them here is enough to satisfy the FK and
        // still leaves old orders fully readable — just no longer linked to a
        // live product row.
        orderItemRepository.detachProduct(id);
        product.getMedia().forEach(m -> s3MediaService.delete(m.getStorageKey()));
        logAudit(product, ProductAuditSection.DELETED, "Deleted the product");
        productRepository.delete(product);
    }

    public ProductResponse setHidden(UUID id, boolean hidden) {
        Product product = getOrThrow(id);
        requireOwnedOrPlatform(product);
        if (product.isHidden() != hidden) {
            product.setHidden(hidden);
            logAudit(product, ProductAuditSection.VISIBILITY, hidden ? "Hid the product" : "Made the product visible");
        }
        return toResponse(product);
    }

    public ProductResponse updateInfo(UUID id, ProductInfoRequest request) {
        Product product = getOrThrow(id);
        requireOwnedOrPlatform(product);
        List<String> changed = new ArrayList<>();

        if (!Objects.equals(product.getName(), request.name())) {
            changed.add("name to \"" + request.name() + "\"");
            product.setName(request.name());
            product.setNameFr(translationService.translateToFrench(request.name()));
        }
        if (!Objects.equals(product.getDescription(), request.description())) {
            changed.add("description");
            product.setDescription(request.description());
            product.setDescriptionFr(translationService.translateToFrench(request.description()));
        }

        Category category = resolveCategory(request.categorySlug());
        Subcategory subcategory = resolveSubcategory(category, request.subcategorySlug());
        boolean categoryChanged = product.getCategory() == null || !Objects.equals(product.getCategory().getSlug(), category.getSlug());
        boolean subcategoryChanged = !Objects.equals(
                product.getSubcategory() != null ? product.getSubcategory().getSlug() : null,
                subcategory != null ? subcategory.getSlug() : null);
        if (categoryChanged || subcategoryChanged) {
            changed.add("category to \"" + category.getSlug() + (subcategory != null ? "/" + subcategory.getSlug() : "") + "\"");
        }
        product.setCategory(category);
        product.setSubcategory(subcategory);

        if (!changed.isEmpty()) {
            logAudit(product, ProductAuditSection.INFO, "Changed " + String.join(", ", changed));
        }
        return toResponse(product);
    }

    public ProductResponse updatePricing(UUID id, ProductPricingRequest request) {
        Product product = getOrThrow(id);
        requireOwnedOrPlatform(product);
        List<String> changed = new ArrayList<>();

        BigDecimal newPrice = request.price() != null ? request.price() : BigDecimal.ZERO;
        if (product.getPrice().compareTo(newPrice) != 0) {
            changed.add("price from " + formatAmount(product.getPrice()) + " to " + formatAmount(newPrice) + " XAF");
            product.setPrice(newPrice);
        }
        BigDecimal oldOriginal = product.getOriginalPrice();
        if (!Objects.equals(oldOriginal, request.originalPrice())) {
            changed.add("original price to " + (request.originalPrice() != null ? formatAmount(request.originalPrice()) + " XAF" : "none"));
            product.setOriginalPrice(request.originalPrice());
        }

        int oldTierCount = product.getBulkPrices().size();
        product.getBulkPrices().clear();
        if (request.bulkPrices() != null) {
            request.bulkPrices().stream()
                    .sorted(java.util.Comparator.comparingInt(BulkPriceTierRequest::quantity))
                    .forEach(tierReq -> product.getBulkPrices().add(new BulkPriceTier(tierReq.quantity(), tierReq.price())));
        }
        if (oldTierCount != product.getBulkPrices().size()) {
            changed.add("bulk-price tiers (" + oldTierCount + " → " + product.getBulkPrices().size() + ")");
        }

        if (!changed.isEmpty()) {
            logAudit(product, ProductAuditSection.PRICING, "Changed " + String.join(", ", changed));
        }
        return toResponse(product);
    }

    public ProductResponse updateColors(UUID id, ProductColorsRequest request) {
        Product product = getOrThrow(id);
        requireOwnedOrPlatform(product);
        List<String> before = product.getColors().stream().map(ProductColor::getName).sorted().toList();
        mergeColors(product, request.colors(), false);
        List<String> after = product.getColors().stream().map(ProductColor::getName).sorted().toList();
        if (!before.equals(after)) {
            logAudit(product, ProductAuditSection.COLORS, "Changed colors (" + before.size() + " → " + after.size() + ")");
        }
        return toResponse(product);
    }

    /**
     * @deprecated superseded by {@link #updateVariantStock} — ProductColor.stock is no longer read anywhere in the
     * order pipeline (see ProductVariant's javadoc). Left in place, unused by the controller, rather than deleted —
     * the field itself is a planned follow-up cleanup once the new per-variant stock editor has been live a while.
     */
    @Deprecated
    public ProductResponse updateStock(UUID id, ProductStockRequest request) {
        Product product = getOrThrow(id);
        requireOwnedOrPlatform(product);
        Map<UUID, ProductColor> byId = new LinkedHashMap<>();
        for (ProductColor c : product.getColors()) {
            byId.put(c.getId(), c);
        }
        List<String> changed = new ArrayList<>();
        if (request.colors() != null) {
            for (ProductStockEntryRequest entry : request.colors()) {
                ProductColor color = byId.get(entry.id());
                if (color == null) continue; // Unknown/removed color — nothing to update.
                if (!Objects.equals(color.getStock(), entry.stock())) {
                    changed.add(color.getName() + " to " + (entry.stock() != null ? entry.stock() : "untracked"));
                    color.setStock(entry.stock());
                }
            }
        }
        if (!changed.isEmpty()) {
            logAudit(product, ProductAuditSection.STOCK, "Set stock: " + String.join(", ", changed));
        }
        return toResponse(product);
    }

    /** The real UPDATE_PRODUCT_STOCK path — upserts the per-(color,size) ledger in ProductVariant. */
    public ProductResponse updateVariantStock(UUID id, ProductVariantStockRequest request) {
        Product product = getOrThrow(id);
        requireOwnedOrPlatform(product);
        List<String> changed = new ArrayList<>();
        for (ProductVariantStockEntryRequest entry : request.variants()) {
            productVariantService.upsertQuantity(product, entry);
            String label = (entry.colorName().isEmpty() ? "" : entry.colorName() + " ") + (entry.size().isEmpty() ? "" : entry.size());
            changed.add((label.isBlank() ? "default" : label.trim()) + " to " + entry.quantity());
        }
        if (!changed.isEmpty()) {
            logAudit(product, ProductAuditSection.STOCK, "Set stock: " + String.join(", ", changed));
        }
        return toResponse(product);
    }

    public ProductResponse updateDisplay(UUID id, ProductDisplayRequest request) {
        Product product = getOrThrow(id);
        requireOwnedOrPlatform(product);
        List<String> changed = new ArrayList<>();

        if (product.getBadge() != request.badge()) {
            changed.add("badge");
            product.setBadge(request.badge());
        }
        List<String> newSizes = request.sizes() != null ? request.sizes() : List.of();
        if (!product.getSizes().equals(newSizes)) {
            changed.add("sizes");
            product.setSizes(newSizes);
        }
        if (request.rating() != null && !Objects.equals(product.getRating(), request.rating())) {
            changed.add("rating");
            product.setRating(request.rating());
        }
        if (request.reviews() != null && !Objects.equals(product.getReviews(), request.reviews())) {
            changed.add("review count");
            product.setReviews(request.reviews());
        }

        if (!changed.isEmpty()) {
            logAudit(product, ProductAuditSection.DISPLAY, "Changed " + String.join(", ", changed));
        }
        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductAuditLogResponse> getAuditLog(UUID productId, Pageable pageable) {
        requireOwnedOrPlatform(getOrThrow(productId));
        return productAuditLogRepository.findByProductIdOrderByChangedAtDesc(productId, pageable).map(this::toAuditResponse);
    }

    /**
     * A cross-product feed, which a per-row ownership check can't filter
     * without either an N+1 product lookup per entry or denormalizing an
     * enterprise id onto every audit row — not worth it for an admin
     * convenience view. Simplest correct behavior until that's built:
     * platform staff only; a scoped admin uses {@link #getAuditLog} per
     * product instead.
     */
    @Transactional(readOnly = true)
    public Page<ProductAuditLogResponse> getAllAuditLog(Pageable pageable) {
        if (CurrentAdmin.enterpriseIdOrNull() != null) {
            throw new ForbiddenException("Not available for a seller account — view a product's own history instead.");
        }
        return productAuditLogRepository.findAllByOrderByChangedAtDesc(pageable).map(this::toAuditResponse);
    }

    public MediaResponse addMedia(UUID productId, MultipartFile file) {
        Product product = getOrThrow(productId);
        requireOwnedOrPlatform(product);
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
        logAudit(product, ProductAuditSection.IMAGES, "Added a picture");
        return toMediaResponse(media);
    }

    public void deleteMedia(UUID mediaId) {
        ProductMedia media = productMediaRepository.findById(mediaId)
                .orElseThrow(() -> ResourceNotFoundException.of("Media", mediaId));
        Product product = media.getProduct();
        if (product != null) {
            requireOwnedOrPlatform(product);
        }
        s3MediaService.delete(media.getStorageKey());
        productMediaRepository.delete(media);
        if (product != null) {
            logAudit(product, ProductAuditSection.IMAGES, "Removed a picture");
        }
    }

    private void applyRequest(Product product, ProductRequest request) {
        Category category = resolveCategory(request.categorySlug());
        Subcategory subcategory = resolveSubcategory(category, request.subcategorySlug());

        String previousName = product.getName();
        String previousDescription = product.getDescription();
        product.setName(request.name());
        product.setDescription(request.description());
        // Only re-translate when the source text actually changed — an
        // admin re-saving the same product otherwise costs a Translate API
        // call for nothing.
        if (!Objects.equals(previousName, request.name())) {
            product.setNameFr(translationService.translateToFrench(request.name()));
        }
        if (!Objects.equals(previousDescription, request.description())) {
            product.setDescriptionFr(translationService.translateToFrench(request.description()));
        }
        product.setPrice(request.price() != null ? request.price() : BigDecimal.ZERO);
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

        mergeColors(product, request.colors(), true);

        product.getBulkPrices().clear();
        if (request.bulkPrices() != null) {
            request.bulkPrices().stream()
                    .sorted(java.util.Comparator.comparingInt(BulkPriceTierRequest::quantity))
                    .forEach(tierReq -> product.getBulkPrices().add(new BulkPriceTier(tierReq.quantity(), tierReq.price())));
        }
    }

    /**
     * Matches incoming color entries against the product's existing ones by
     * id so an update in place preserves the row (and, when {@code
     * applyStock} is false, its current stock — used by the colors-only
     * section endpoint, which isn't allowed to touch stock). An entry with
     * no id (or one that doesn't match anything existing) becomes a new
     * color; any existing color absent from the incoming list is dropped
     * (orphanRemoval on {@link Product#getColors()} handles the delete).
     */
    private void mergeColors(Product product, List<ProductColorRequest> requested, boolean applyStock) {
        Map<UUID, ProductColor> existingById = new LinkedHashMap<>();
        for (ProductColor c : product.getColors()) {
            if (c.getId() != null) {
                existingById.put(c.getId(), c);
            }
        }
        List<ProductColor> merged = new ArrayList<>();
        if (requested != null) {
            for (ProductColorRequest req : requested) {
                ProductColor color = req.id() != null ? existingById.remove(req.id()) : null;
                if (color == null) {
                    color = ProductColor.builder().product(product).build();
                }
                color.setName(req.name());
                color.setHex(req.hex());
                if (applyStock) {
                    color.setStock(req.stock());
                }
                merged.add(color);
            }
        }
        product.getColors().clear();
        product.getColors().addAll(merged);
    }

    private Category resolveCategory(String categorySlug) {
        return categoryRepository.findBySlug(categorySlug)
                .orElseThrow(() -> new BadRequestException("Unknown category: " + categorySlug));
    }

    private Subcategory resolveSubcategory(Category category, String subcategorySlug) {
        if (subcategorySlug == null || subcategorySlug.isBlank()) {
            return null;
        }
        return category.getSubcategories().stream()
                .filter(s -> s.getSlug().equals(subcategorySlug))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Unknown subcategory '" + subcategorySlug + "' for category '" + category.getSlug() + "'"));
    }

    private void logAudit(Product product, ProductAuditSection section, String summary) {
        productAuditLogRepository.save(ProductAuditLog.builder()
                .productId(product.getId())
                .productName(product.getName())
                .section(section)
                .summary(summary)
                .changedByName(CurrentAdmin.nameOrNull())
                .changedById(CurrentAdmin.idOrNull())
                .build());
    }

    private ProductAuditLogResponse toAuditResponse(ProductAuditLog log) {
        return new ProductAuditLogResponse(
                log.getId(), log.getProductId(), log.getProductName(),
                log.getSection().name(), log.getSummary(), log.getChangedByName(), log.getChangedAt());
    }

    private String formatAmount(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
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
        List<BulkPriceTierResponse> bulkPrices = p.getBulkPrices().stream()
                .map(t -> new BulkPriceTierResponse(t.getQuantity(), t.getPrice()))
                .toList();
        List<MediaResponse> media = p.getMedia().stream().map(this::toMediaResponse).toList();
        List<ProductVariantResponse> variants = p.getVariants().stream()
                .map(v -> new ProductVariantResponse(v.getId(), v.getColorName(), v.getSize(), v.getQuantity(), v.isUntracked()))
                .toList();

        return new ProductResponse(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getNameFr(),
                p.getDescriptionFr(),
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
                variants,
                bulkPrices,
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
                m.getUploadedAt()
        );
    }
}
