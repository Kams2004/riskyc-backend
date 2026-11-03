package com.fashion.Riskyc.service;

import com.fashion.Riskyc.entity.Enterprise;
import com.fashion.Riskyc.entity.Product;
import com.fashion.Riskyc.exception.ForbiddenException;
import com.fashion.Riskyc.repository.CategoryRepository;
import com.fashion.Riskyc.repository.EnterpriseRepository;
import com.fashion.Riskyc.repository.OrderItemRepository;
import com.fashion.Riskyc.repository.ProductAuditLogRepository;
import com.fashion.Riskyc.repository.ProductMediaRepository;
import com.fashion.Riskyc.repository.ProductRepository;
import com.fashion.Riskyc.repository.SubcategoryRepository;
import com.fashion.Riskyc.security.AdminPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Codifies the requireOwnedOrPlatform() guard that every scoped admin's
 * product mutation goes through — previously only proven by hand via
 * psql+curl against the dev stack. Exercised through setHidden(), the
 * simplest mutation that reaches the guard.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductMediaRepository productMediaRepository;
    @Mock
    private ProductAuditLogRepository productAuditLogRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private SubcategoryRepository subcategoryRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private S3MediaService s3MediaService;
    @Mock
    private TranslationService translationService;
    @Mock
    private ProductVariantService productVariantService;
    @Mock
    private EnterpriseRepository enterpriseRepository;

    @InjectMocks
    private ProductService productService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void actAs(String enterpriseId) {
        AdminPrincipal principal = new AdminPrincipal(
                UUID.randomUUID().toString(), "admin@example.com", "Test Admin",
                UUID.randomUUID().toString(), "Owner", Set.of(), enterpriseId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null));
    }

    @Test
    void setHidden_scopedAdminBlockedFromADifferentEnterprisesProduct() {
        Enterprise otherEnterprise = Enterprise.builder().id(UUID.randomUUID()).name("Other Seller").build();
        Product product = Product.builder().id(UUID.randomUUID()).name("Dress").enterprise(otherEnterprise).build();
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));

        actAs(UUID.randomUUID().toString());

        assertThatThrownBy(() -> productService.setHidden(product.getId(), true))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void setHidden_scopedAdminAllowedForTheirOwnEnterprisesProduct() {
        UUID enterpriseId = UUID.randomUUID();
        Enterprise myEnterprise = Enterprise.builder().id(enterpriseId).name("My Shop").build();
        Product product = Product.builder().id(UUID.randomUUID()).name("Dress").enterprise(myEnterprise).build();
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));

        actAs(enterpriseId.toString());

        productService.setHidden(product.getId(), true);

        assertThat(product.isHidden()).isTrue();
    }

    @Test
    void setHidden_platformAdminNeverBlockedRegardlessOfEnterprise() {
        Enterprise someEnterprise = Enterprise.builder().id(UUID.randomUUID()).name("Any Seller").build();
        Product product = Product.builder().id(UUID.randomUUID()).name("Dress").enterprise(someEnterprise).build();
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));

        // No security context at all — mirrors a platform admin's unscoped JWT, same as every public/admin
        // endpoint in this codebase already defaults to when CurrentAdmin.get() is empty.

        productService.setHidden(product.getId(), true);

        assertThat(product.isHidden()).isTrue();
    }

    @Test
    void setHidden_productWithNoEnterpriseIsTreatedAsNotOwnedByAnyScopedAdmin() {
        // Shouldn't exist post-backfill, but the guard must fail closed, not open.
        Product product = Product.builder().id(UUID.randomUUID()).name("Orphan Product").enterprise(null).build();
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));

        actAs(UUID.randomUUID().toString());

        assertThatThrownBy(() -> productService.setHidden(product.getId(), true))
                .isInstanceOf(ForbiddenException.class);
    }
}
