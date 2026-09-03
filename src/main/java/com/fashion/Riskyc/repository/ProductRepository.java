package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    /**
     * A single query for the whole catalog (or a page of it) — collection
     * associations (colors, media) are then resolved via Hibernate's
     * batched lazy loading (see {@code hibernate.default_batch_fetch_size}),
     * never one-query-per-product.
     */
    Page<Product> findByHiddenFalse(Pageable pageable);

    List<Product> findByHiddenFalse();

    Page<Product> findByCategorySlugAndHiddenFalse(String categorySlug, Pageable pageable);

    Page<Product> findByCategorySlugAndSubcategorySlugAndHiddenFalse(
            String categorySlug, String subcategorySlug, Pageable pageable);

    boolean existsByCategoryId(UUID categoryId);

    boolean existsBySubcategoryId(UUID subcategoryId);

    /** Storefront-visible product count per category — used to hide empty categories from the nav/footer. */
    long countByCategoryIdAndHiddenFalse(UUID categoryId);

    /** Same, per subcategory — used to hide empty subcategories from the same surfaces. */
    long countBySubcategoryIdAndHiddenFalse(UUID subcategoryId);

    @Query("""
            select p from Product p
            where p.hidden = false
              and (lower(p.name) like lower(concat('%', :q, '%'))
                   or exists (select 1 from p.tags t where lower(t) like lower(concat('%', :q, '%'))))
            """)
    Page<Product> search(@Param("q") String query, Pageable pageable);
}
