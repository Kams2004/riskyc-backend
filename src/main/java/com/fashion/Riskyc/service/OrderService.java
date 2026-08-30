package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.request.CreateOrderRequest;
import com.fashion.Riskyc.dto.request.CustomerInfoRequest;
import com.fashion.Riskyc.dto.request.OrderItemRequest;
import com.fashion.Riskyc.dto.response.CustomerInfoResponse;
import com.fashion.Riskyc.dto.response.OrderItemResponse;
import com.fashion.Riskyc.dto.response.OrderResponse;
import com.fashion.Riskyc.entity.*;
import com.fashion.Riskyc.exception.BadRequestException;
import com.fashion.Riskyc.exception.ConflictException;
import com.fashion.Riskyc.exception.ResourceNotFoundException;
import com.fashion.Riskyc.repository.CustomerRepository;
import com.fashion.Riskyc.repository.OrderRepository;
import com.fashion.Riskyc.repository.ProductRepository;
import com.fashion.Riskyc.security.CurrentAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private static final String PAYMENT_PROOF_FOLDER = "payment-proofs";
    /** Every connected admin session subscribes here to keep order/packaging state in sync live. */
    private static final String ORDERS_TOPIC = "/topic/orders";

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final S3MediaService s3MediaService;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final PushNotificationService pushNotificationService;

    @Value("${app.site.url}")
    private String siteUrl;

    @Value("${app.payment.orange-money-code}")
    private String orangeMoneyCode;

    @Value("${app.payment.mobile-money-code}")
    private String mobileMoneyCode;

    @Value("${app.payment.free-delivery-threshold}")
    private BigDecimal freeDeliveryThreshold;

    @Value("${app.payment.delivery-fee}")
    private BigDecimal deliveryFee;

    @Transactional(readOnly = true)
    public List<OrderResponse> listAll() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listForCustomer(UUID customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(UUID id) {
        return toResponse(getOrThrow(id));
    }

    public OrderResponse create(CreateOrderRequest request) {
        Customer customer = null;
        if (request.customerId() != null) {
            customer = customerRepository.findById(request.customerId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Customer", request.customerId()));
        }

        Order order = Order.builder()
                .customer(customer)
                .status(OrderStatus.PENDING)
                .customerInfo(toEmbeddable(request.customerInfo()))
                .total(BigDecimal.ZERO)
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItemRequest itemReq : request.items()) {
            Product product = productRepository.findById(itemReq.productId())
                    .orElseThrow(() -> new BadRequestException("Product not found: " + itemReq.productId()));

            BigDecimal lineTotal = computeLineTotal(product.getPrice(), product.getBulkPrices(), itemReq.quantity());
            subtotal = subtotal.add(lineTotal);
            BigDecimal effectiveUnitPrice = lineTotal.divide(BigDecimal.valueOf(itemReq.quantity()), 2, java.math.RoundingMode.HALF_UP);

            order.getItems().add(OrderItem.builder()
                    .order(order)
                    .product(product)
                    .productName(product.getName())
                    .quantity(itemReq.quantity())
                    .selectedColor(itemReq.selectedColor())
                    .selectedSize(itemReq.selectedSize())
                    .selectedImageIndex(itemReq.selectedImageIndex())
                    .unitPrice(effectiveUnitPrice)
                    .build());
        }

        boolean freeDelivery = subtotal.compareTo(freeDeliveryThreshold) >= 0;
        order.setTotal(freeDelivery ? subtotal : subtotal.add(deliveryFee));

        Order saved = orderRepository.saveAndFlush(order);
        notificationService.notifyAdmin(NotificationType.NEW_ORDER,
                "New order placed for " + formatAmount(saved.getTotal()) + " XAF", saved.getId().toString());
        return toResponse(saved);
    }

    public OrderResponse setPaymentMethod(UUID orderId, PaymentMethod method) {
        Order order = getOrThrow(orderId);
        order.setPaymentMethod(method);
        order.setPaymentCode(method == PaymentMethod.ORANGE_MONEY ? orangeMoneyCode : mobileMoneyCode);
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        return toResponse(order);
    }

    public OrderResponse uploadPaymentProof(UUID orderId, MultipartFile file) {
        Order order = getOrThrow(orderId);
        String key = s3MediaService.upload(file, PAYMENT_PROOF_FOLDER + "/" + orderId);
        order.setPaymentScreenshotKey(key);
        order.setStatus(OrderStatus.REVIEWING);
        OrderResponse response = toResponse(order);
        notificationService.notifyAdmin(NotificationType.PAYMENT_PROOF_UPLOADED,
                "Payment proof uploaded for order " + orderId, orderId.toString());
        return response;
    }

    public OrderResponse updateStatus(UUID orderId, OrderStatus status, String reason) {
        Order order = getOrThrow(orderId);
        order.setStatus(status);
        order.setStatusChangedByName(CurrentAdmin.nameOrNull());
        order.setStatusChangedAt(Instant.now());
        if (status == OrderStatus.CANCELLED) {
            order.setRejectionReason(reason);
        }
        OrderResponse response = toResponse(order);
        if (order.getCustomer() != null) {
            notificationService.notifyCustomer(order.getCustomer().getId(), NotificationType.ORDER_STATUS_CHANGED,
                    "Your order status changed to " + status, orderId.toString());
        }
        String trackingUrl = siteUrl + "/track/" + orderId;
        if (status == OrderStatus.VALIDATED) {
            pushNotificationService.notifyOrder(orderId, "Payment confirmed!",
                    "Your payment has been validated — tap to track your order.", trackingUrl);
        } else if (status == OrderStatus.CANCELLED) {
            pushNotificationService.notifyOrder(orderId, "Order rejected",
                    "We couldn't validate your payment — tap for details.", trackingUrl);
        }
        messagingTemplate.convertAndSend(ORDERS_TOPIC, response);
        return response;
    }

    /**
     * Claims a validated order for packaging. Atomic against the loaded row's
     * current status — if another admin already started it (or the order
     * isn't in a packageable state at all), this throws instead of silently
     * overwriting who's doing the work.
     */
    public OrderResponse startPackaging(UUID orderId) {
        Order order = getOrThrow(orderId);
        if (order.getStatus() != OrderStatus.VALIDATED) {
            if (order.getStatus() == OrderStatus.PACKAGING) {
                throw new ConflictException("Already being packaged by " + order.getPackagingStartedByName());
            }
            throw new ConflictException("Order isn't in a packageable state (currently " + order.getStatus() + ")");
        }
        order.setStatus(OrderStatus.PACKAGING);
        order.setPackagingStartedByName(CurrentAdmin.nameOrNull());
        order.setPackagingStartedAt(Instant.now());
        OrderResponse response = toResponse(order);
        messagingTemplate.convertAndSend(ORDERS_TOPIC, response);
        return response;
    }

    /** Marks an in-progress packaging job as done. */
    public OrderResponse completePackaging(UUID orderId) {
        Order order = getOrThrow(orderId);
        if (order.getStatus() != OrderStatus.PACKAGING) {
            throw new ConflictException("Order isn't currently being packaged (status is " + order.getStatus() + ")");
        }
        order.setStatus(OrderStatus.PACKAGED);
        order.setPackagingCompletedByName(CurrentAdmin.nameOrNull());
        order.setPackagingCompletedAt(Instant.now());
        OrderResponse response = toResponse(order);
        messagingTemplate.convertAndSend(ORDERS_TOPIC, response);
        return response;
    }

    /**
     * Prices one order line, applying the product's bulk/grouped-pricing
     * tiers if it has any (mirrors lib/pricing.ts on the frontend, which
     * shows the customer this same figure before they ever submit the
     * order): greedily applies as many of the largest-quantity tier as fit,
     * then the next-largest for whatever remains, and so on; anything left
     * over once no tier fits is charged at the regular unit price. E.g. a
     * 10-for-20,000 tier with a quantity of 12 → one tier (20,000) plus 2
     * units at the regular unit price — never a partial/prorated tier rate.
     */
    private BigDecimal computeLineTotal(BigDecimal unitPrice, List<BulkPriceTier> bulkPrices, int quantity) {
        if (quantity <= 0) return BigDecimal.ZERO;

        List<BulkPriceTier> tiers = bulkPrices.stream()
                .filter(t -> t.getQuantity() != null && t.getPrice() != null && t.getQuantity() > 0 && t.getPrice().signum() > 0)
                .sorted(java.util.Comparator.comparingInt(BulkPriceTier::getQuantity).reversed())
                .toList();

        int remaining = quantity;
        BigDecimal total = BigDecimal.ZERO;
        for (BulkPriceTier tier : tiers) {
            if (remaining >= tier.getQuantity()) {
                int count = remaining / tier.getQuantity();
                total = total.add(tier.getPrice().multiply(BigDecimal.valueOf(count)));
                remaining -= count * tier.getQuantity();
            }
        }
        if (remaining > 0) {
            total = total.add(unitPrice.multiply(BigDecimal.valueOf(remaining)));
        }
        return total;
    }

    private Order getOrThrow(UUID id) {
        return orderRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Order", id));
    }

    private CustomerInfo toEmbeddable(CustomerInfoRequest r) {
        if (r == null) return null;
        return CustomerInfo.builder()
                .firstName(r.firstName())
                .lastName(r.lastName())
                .phone(r.phone())
                .town(r.town())
                .street(r.street())
                .deliveryType(r.deliveryType())
                .build();
    }

    private CustomerInfoResponse toResponse(CustomerInfo info) {
        if (info == null) return null;
        return new CustomerInfoResponse(info.getFirstName(), info.getLastName(), info.getPhone(),
                info.getTown(), info.getStreet(), info.getDeliveryType());
    }

    private String formatAmount(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    private OrderResponse toResponse(Order o) {
        List<OrderItemResponse> items = o.getItems().stream().map(this::toResponse).toList();
        String paymentScreenshotUrl = o.getPaymentScreenshotKey() != null
                ? s3MediaService.getPresignedUrl(o.getPaymentScreenshotKey())
                : null;

        return new OrderResponse(
                o.getId(),
                o.getCustomer() != null ? o.getCustomer().getId() : null,
                items,
                o.getTotal(),
                o.getStatus(),
                toResponse(o.getCustomerInfo()),
                o.getPaymentMethod(),
                o.getPaymentCode(),
                paymentScreenshotUrl,
                o.getStatusChangedByName(),
                o.getStatusChangedAt(),
                o.getRejectionReason(),
                o.getPackagingStartedByName(),
                o.getPackagingStartedAt(),
                o.getPackagingCompletedByName(),
                o.getPackagingCompletedAt(),
                o.getCreatedAt(),
                o.getUpdatedAt()
        );
    }

    private OrderItemResponse toResponse(OrderItem item) {
        String thumbnailUrl = null;
        Product product = item.getProduct();
        if (product != null && !product.getMedia().isEmpty()) {
            // Prefer the exact photo the customer picked (via "quantity by photo"); fall back to
            // the first image for everything else, or if that index no longer exists.
            Integer idx = item.getSelectedImageIndex();
            ProductMedia media = (idx != null && idx >= 0 && idx < product.getMedia().size())
                    ? product.getMedia().get(idx)
                    : product.getMedia().get(0);
            thumbnailUrl = s3MediaService.getPresignedUrl(media.getStorageKey());
        }
        return new OrderItemResponse(
                item.getId(),
                product != null ? product.getId() : null,
                item.getProductName(),
                thumbnailUrl,
                item.getQuantity(),
                item.getSelectedColor(),
                item.getSelectedSize(),
                item.getSelectedImageIndex(),
                item.getUnitPrice()
        );
    }
}
