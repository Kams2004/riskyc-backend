package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.EnterpriseStatus;
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
     *
     * The "AndEnterprise_StatusNot(SUSPENDED)" clause is how a suspended
     * seller's whole catalog disappears from every public listing without
     * touching each product's own {@code hidden} flag — see
     * EnterpriseService#suspend()'s own reasoning. A product with no
     * enterprise at all (shouldn't exist post-backfill, but a safe default
     * either way) is excluded too, since the generated join can't match a
     * null against "not suspended."
     */
    Page<Product> findByHiddenFalseAndEnterprise_StatusNot(EnterpriseStatus excludedStatus, Pageable pageable);

    List<Product> findByHiddenFalseAndEnterprise_StatusNot(EnterpriseStatus excludedStatus);

    Page<Product> findByCategorySlugAndHiddenFalseAndEnterprise_StatusNot(
            String categorySlug, EnterpriseStatus excludedStatus, Pageable pageable);

    Page<Product> findByCategorySlugAndSubcategorySlugAndHiddenFalseAndEnterprise_StatusNot(
            String categorySlug, String subcategorySlug, EnterpriseStatus excludedStatus, Pageable pageable);

    /** Admin listing scoped to one enterprise — unlike findAll(), used when the acting admin isn't platform staff. */
    List<Product> findByEnterpriseId(UUID enterpriseId);

    boolean existsByCategoryId(UUID categoryId);

    boolean existsBySubcategoryId(UUID subcategoryId);

    /** Storefront-visible product count per category — used to hide empty categories from the nav/footer. */
    long countByCategoryIdAndHiddenFalse(UUID categoryId);

    /** Same, per subcategory — used to hide empty subcategories from the same surfaces. */
    long countBySubcategoryIdAndHiddenFalse(UUID subcategoryId);

    @Query("""
            select p from Product p
            where p.hidden = false
              and p.enterprise.status <> :excludedStatus
              and (lower(p.name) like lower(concat('%', :q, '%'))
                   or exists (select 1 from p.tags t where lower(t) like lower(concat('%', :q, '%'))))
            """)
    Page<Product> search(@Param("q") String query, @Param("excludedStatus") EnterpriseStatus excludedStatus, Pageable pageable);
}
