package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.request.CreateOrderRequest;
import com.fashion.Riskyc.dto.request.CustomerInfoRequest;
import com.fashion.Riskyc.dto.request.OrderItemRequest;
import com.fashion.Riskyc.dto.response.OrderResponse;
import com.fashion.Riskyc.entity.DeliveryType;
import com.fashion.Riskyc.entity.Enterprise;
import com.fashion.Riskyc.entity.EnterpriseStatus;
import com.fashion.Riskyc.entity.Product;
import com.fashion.Riskyc.entity.ProductVariant;
import com.fashion.Riskyc.exception.BadRequestException;
import com.fashion.Riskyc.repository.CustomerRepository;
import com.fashion.Riskyc.repository.OrderRepository;
import com.fashion.Riskyc.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the multi-seller order-splitting logic in create() — previously
 * only proven by hand via a manually-seeded second enterprise + curl against
 * the dev stack.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private ProductVariantService productVariantService;
    @Mock
    private S3MediaService s3MediaService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private PushNotificationService pushNotificationService;
    @Mock
    private SmsService smsService;
    @Mock
    private TwilioSmsService twilioSmsService;
    @Mock
    private ConversationService conversationService;
    @Mock
    private PaymentAccountService paymentAccountService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        // PricingService has no dependencies of its own — use the real
        // implementation rather than a mock, so the split's line totals are
        // computed for real, not just stubbed to whatever the test wants.
        orderService = new OrderService(
                orderRepository, productRepository, customerRepository, productVariantService,
                s3MediaService, notificationService, messagingTemplate, pushNotificationService,
                smsService, twilioSmsService, conversationService, new PricingService(), paymentAccountService);
        // Every shop accepts payments unless a test says otherwise.
        org.mockito.Mockito.lenient().when(paymentAccountService.acceptsPayments(any())).thenReturn(true);

        // lenient: the suspended-seller rejection test never reaches saveAndFlush at all.
        org.mockito.Mockito.lenient().when(orderRepository.saveAndFlush(any())).thenAnswer(inv -> {
            com.fashion.Riskyc.entity.Order order = inv.getArgument(0);
            if (order.getId() == null) order.setId(UUID.randomUUID());
            return order;
        });
    }

    private Product productFor(Enterprise enterprise, BigDecimal price) {
        return Product.builder().id(UUID.randomUUID()).name("Test Product").price(price).enterprise(enterprise).build();
    }

    private void stubVariant(Product product, int availableQuantity) {
        ProductVariant variant = ProductVariant.builder().id(UUID.randomUUID()).product(product).quantity(availableQuantity).build();
        when(productVariantService.resolveForUpdate(eq(product), org.mockito.ArgumentMatchers.nullable(String.class), org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenReturn(variant);
    }

    private CreateOrderRequest requestFor(List<OrderItemRequest> items) {
        return new CreateOrderRequest(
                null, items,
                new CustomerInfoRequest("Jane", "Doe", "+221700000000", "Douala", "Rue de la Paix", DeliveryType.PICKUP),
                "en");
    }

    @Test
    void create_singleEnterpriseCart_producesExactlyOneOrder() {
        Enterprise enterprise = Enterprise.builder().id(UUID.randomUUID()).name("Riskyc Fashion").platformDefault(true).build();
        Product productA = productFor(enterprise, new BigDecimal("3000"));
        Product productB = productFor(enterprise, new BigDecimal("5000"));
        when(productRepository.findById(productA.getId())).thenReturn(java.util.Optional.of(productA));
        when(productRepository.findById(productB.getId())).thenReturn(java.util.Optional.of(productB));
        stubVariant(productA, 10);
        stubVariant(productB, 10);

        CreateOrderRequest request = requestFor(List.of(
                new OrderItemRequest(productA.getId(), 1, null, null, null),
                new OrderItemRequest(productB.getId(), 2, null, null, null)));

        List<OrderResponse> orders = orderService.create(request);

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).total()).isEqualByComparingTo("13000.00"); // 3000 + 2*5000
        assertThat(orders.get(0).items()).hasSize(2);
        assertThat(orders.get(0).checkoutGroupId()).isNotNull();
        assertThat(orders.get(0).enterpriseId()).isEqualTo(enterprise.getId());
    }

    @Test
    void create_multiEnterpriseCart_splitsIntoOneOrderPerSeller() {
        Enterprise platformSeller = Enterprise.builder().id(UUID.randomUUID()).name("Platform Seller").platformDefault(true).build();
        Enterprise secondSeller = Enterprise.builder().id(UUID.randomUUID()).name("Second Seller").build();
        Product platformProduct = productFor(platformSeller, new BigDecimal("3000"));
        Product secondSellerProduct = productFor(secondSeller, new BigDecimal("7000"));
        when(productRepository.findById(platformProduct.getId())).thenReturn(java.util.Optional.of(platformProduct));
        when(productRepository.findById(secondSellerProduct.getId())).thenReturn(java.util.Optional.of(secondSellerProduct));
        stubVariant(platformProduct, 10);
        stubVariant(secondSellerProduct, 10);

        CreateOrderRequest request = requestFor(List.of(
                new OrderItemRequest(platformProduct.getId(), 1, null, null, null),
                new OrderItemRequest(secondSellerProduct.getId(), 2, null, null, null)));

        List<OrderResponse> orders = orderService.create(request);

        assertThat(orders).hasSize(2);
        // Every sub-order from the same checkout shares one group id.
        assertThat(orders).extracting(OrderResponse::checkoutGroupId).doesNotContainNull();
        assertThat(orders.get(0).checkoutGroupId()).isEqualTo(orders.get(1).checkoutGroupId());

        OrderResponse platformOrder = orders.stream().filter(o -> o.enterpriseId().equals(platformSeller.getId())).findFirst().orElseThrow();
        OrderResponse secondOrder = orders.stream().filter(o -> o.enterpriseId().equals(secondSeller.getId())).findFirst().orElseThrow();

        assertThat(platformOrder.total()).isEqualByComparingTo("3000.00");
        assertThat(platformOrder.items()).hasSize(1);
        assertThat(secondOrder.total()).isEqualByComparingTo("14000.00"); // 2 * 7000
        assertThat(secondOrder.items()).hasSize(1);
    }

    @Test
    void create_decrementsStockForEveryLineRegardlessOfWhichSellerItBelongsTo() {
        Enterprise platformSeller = Enterprise.builder().id(UUID.randomUUID()).name("Platform Seller").platformDefault(true).build();
        Enterprise secondSeller = Enterprise.builder().id(UUID.randomUUID()).name("Second Seller").build();
        Product platformProduct = productFor(platformSeller, new BigDecimal("3000"));
        Product secondSellerProduct = productFor(secondSeller, new BigDecimal("7000"));
        when(productRepository.findById(platformProduct.getId())).thenReturn(java.util.Optional.of(platformProduct));
        when(productRepository.findById(secondSellerProduct.getId())).thenReturn(java.util.Optional.of(secondSellerProduct));
        stubVariant(platformProduct, 10);
        stubVariant(secondSellerProduct, 10);

        CreateOrderRequest request = requestFor(List.of(
                new OrderItemRequest(platformProduct.getId(), 1, null, null, null),
                new OrderItemRequest(secondSellerProduct.getId(), 2, null, null, null)));

        orderService.create(request);

        verify(productVariantService, org.mockito.Mockito.times(2)).decrementStrict(any(), anyInt());
    }

    @Test
    void create_rejectsAProductWhoseSellerIsSuspended_evenIfAlreadyInTheCart() {
        Enterprise suspendedSeller = Enterprise.builder().id(UUID.randomUUID()).name("Suspended Seller").status(EnterpriseStatus.SUSPENDED).build();
        Product product = productFor(suspendedSeller, new BigDecimal("3000"));
        when(productRepository.findById(product.getId())).thenReturn(java.util.Optional.of(product));

        CreateOrderRequest request = requestFor(List.of(new OrderItemRequest(product.getId(), 1, null, null, null)));

        assertThatThrownBy(() -> orderService.create(request)).isInstanceOf(BadRequestException.class);
        verify(orderRepository, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    @Test
    void create_refusesAShopThatHasNotSetUpItsPaymentNumbers() {
        Enterprise shop = Enterprise.builder().id(UUID.randomUUID()).name("Nouvelle Boutique").build();
        Product product = productFor(shop, new BigDecimal("5000"));
        when(productRepository.findById(product.getId())).thenReturn(java.util.Optional.of(product));
        when(paymentAccountService.acceptsPayments(shop)).thenReturn(false);

        var request = new com.fashion.Riskyc.dto.request.CreateOrderRequest(null,
                List.of(new com.fashion.Riskyc.dto.request.OrderItemRequest(product.getId(), 1, null, null, null)), null, "fr");
        var e = org.junit.jupiter.api.Assertions.assertThrows(com.fashion.Riskyc.exception.ConflictException.class,
                () -> orderService.create(request));
        org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("Nouvelle Boutique"));
        org.mockito.Mockito.verify(orderRepository, org.mockito.Mockito.never()).saveAndFlush(any());
    }
}
