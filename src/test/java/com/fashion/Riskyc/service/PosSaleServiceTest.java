package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.request.CreatePosSaleRequest;
import com.fashion.Riskyc.dto.request.PosSaleItemRequest;
import com.fashion.Riskyc.dto.response.PosSaleResponse;
import com.fashion.Riskyc.entity.Enterprise;
import com.fashion.Riskyc.entity.PosPaymentMethod;
import com.fashion.Riskyc.entity.PosSale;
import com.fashion.Riskyc.entity.Product;
import com.fashion.Riskyc.entity.ProductVariant;
import com.fashion.Riskyc.exception.ForbiddenException;
import com.fashion.Riskyc.repository.EnterpriseRepository;
import com.fashion.Riskyc.repository.PosSaleItemRepository;
import com.fashion.Riskyc.repository.PosSaleRepository;
import com.fashion.Riskyc.repository.ProductRepository;
import com.fashion.Riskyc.security.AdminPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Covers a gap found during the marketplace hardening review: PosSaleService
 * had zero enterprise-scoping at all — any admin with POS access could ring
 * up a sale (and adjust stock) against any seller's product, not just their
 * own. This codifies the fix: a scoped admin's till can only sell their own
 * enterprise's products and only ever sees their own sales.
 */
@ExtendWith(MockitoExtension.class)
class PosSaleServiceTest {

    @Mock
    private PosSaleRepository posSaleRepository;
    @Mock
    private PosSaleItemRepository posSaleItemRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductVariantService productVariantService;
    @Mock
    private S3MediaService s3MediaService;
    @Mock
    private EnterpriseRepository enterpriseRepository;

    private PosSaleService posSaleService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void init() {
        posSaleService = new PosSaleService(
                posSaleRepository, posSaleItemRepository, productRepository, productVariantService,
                s3MediaService, new PricingService(), enterpriseRepository);
    }

    private void actAsScopedAdmin(String enterpriseId) {
        AdminPrincipal principal = new AdminPrincipal(
                UUID.randomUUID().toString(), "seller@example.com", "Seller Cashier",
                UUID.randomUUID().toString(), "Owner", Set.of(), enterpriseId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null));
    }

    private Product productFor(Enterprise enterprise, BigDecimal price) {
        return Product.builder().id(UUID.randomUUID()).name("Test Product").price(price).enterprise(enterprise).build();
    }

    private void stubVariant(Product product, int availableQuantity) {
        ProductVariant variant = ProductVariant.builder().id(UUID.randomUUID()).product(product).quantity(availableQuantity).build();
        lenient().when(productVariantService.resolveForUpdate(eq(product), any(), any())).thenReturn(variant);
    }

    private CreatePosSaleRequest requestFor(List<PosSaleItemRequest> items) {
        return new CreatePosSaleRequest(UUID.randomUUID(), items, PosPaymentMethod.CASH, Instant.now());
    }

    private void stubSave() {
        lenient().when(posSaleRepository.saveAndFlush(any())).thenAnswer(inv -> {
            PosSale sale = inv.getArgument(0);
            if (sale.getId() == null) sale.setId(UUID.randomUUID());
            return sale;
        });
    }

    @Test
    void create_scopedAdminBlockedFromSellingADifferentEnterprisesProduct() {
        init();
        Enterprise otherSeller = Enterprise.builder().id(UUID.randomUUID()).name("Other Seller").build();
        Product product = productFor(otherSeller, new BigDecimal("3000"));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(posSaleRepository.findByClientSaleId(any())).thenReturn(Optional.empty());

        actAsScopedAdmin(UUID.randomUUID().toString());

        CreatePosSaleRequest request = requestFor(List.of(new PosSaleItemRequest(product.getId(), 1, "", "")));

        assertThatThrownBy(() -> posSaleService.create(request)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void create_scopedAdminCanSellTheirOwnEnterprisesProductAndTheSaleIsStamped() {
        init();
        UUID enterpriseId = UUID.randomUUID();
        Enterprise myShop = Enterprise.builder().id(enterpriseId).name("My Shop").build();
        Product product = productFor(myShop, new BigDecimal("3000"));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(posSaleRepository.findByClientSaleId(any())).thenReturn(Optional.empty());
        when(enterpriseRepository.findById(enterpriseId)).thenReturn(Optional.of(myShop));
        stubVariant(product, 10);
        stubSave();

        actAsScopedAdmin(enterpriseId.toString());

        CreatePosSaleRequest request = requestFor(List.of(new PosSaleItemRequest(product.getId(), 2, "", "")));
        PosSaleResponse response = posSaleService.create(request);

        assertThat(response.enterpriseId()).isEqualTo(enterpriseId);
        assertThat(response.total()).isEqualByComparingTo("6000.00");
    }

    @Test
    void create_platformAdminCanStillSellAnySellersProductUnrestricted() {
        init();
        Enterprise someSeller = Enterprise.builder().id(UUID.randomUUID()).name("Any Seller").build();
        Product product = productFor(someSeller, new BigDecimal("3000"));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(posSaleRepository.findByClientSaleId(any())).thenReturn(Optional.empty());
        stubVariant(product, 10);
        stubSave();

        // No security context — mirrors an unscoped platform admin's JWT.

        CreatePosSaleRequest request = requestFor(List.of(new PosSaleItemRequest(product.getId(), 1, "", "")));
        PosSaleResponse response = posSaleService.create(request);

        assertThat(response.enterpriseId()).isEqualTo(someSeller.getId());
    }

    @Test
    void voidSale_scopedAdminBlockedFromVoidingADifferentEnterprisesSale() {
        init();
        Enterprise otherSeller = Enterprise.builder().id(UUID.randomUUID()).name("Other Seller").build();
        PosSale sale = PosSale.builder().id(UUID.randomUUID()).clientSaleId(UUID.randomUUID()).cashierName("X")
                .paymentMethod(PosPaymentMethod.CASH).soldAt(Instant.now()).total(BigDecimal.TEN).enterprise(otherSeller).build();
        when(posSaleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));

        actAsScopedAdmin(UUID.randomUUID().toString());

        assertThatThrownBy(() -> posSaleService.voidSale(sale.getId())).isInstanceOf(ForbiddenException.class);
    }
}
