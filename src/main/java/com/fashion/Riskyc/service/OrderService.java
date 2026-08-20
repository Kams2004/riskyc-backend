package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.request.CreateOrderRequest;
import com.fashion.Riskyc.dto.request.CustomerInfoRequest;
import com.fashion.Riskyc.dto.request.OrderItemRequest;
import com.fashion.Riskyc.dto.response.CustomerInfoResponse;
import com.fashion.Riskyc.dto.response.OrderItemResponse;
import com.fashion.Riskyc.dto.response.OrderResponse;
import com.fashion.Riskyc.entity.*;
import com.fashion.Riskyc.exception.BadRequestException;
import com.fashion.Riskyc.exception.ResourceNotFoundException;
import com.fashion.Riskyc.repository.CustomerRepository;
import com.fashion.Riskyc.repository.OrderRepository;
import com.fashion.Riskyc.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private static final String PAYMENT_PROOF_FOLDER = "payment-proofs";

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final S3MediaService s3MediaService;
    private final NotificationService notificationService;

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

            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(itemReq.quantity()));
            subtotal = subtotal.add(lineTotal);

            order.getItems().add(OrderItem.builder()
                    .order(order)
                    .product(product)
                    .productName(product.getName())
                    .quantity(itemReq.quantity())
                    .selectedColor(itemReq.selectedColor())
                    .selectedSize(itemReq.selectedSize())
                    .unitPrice(product.getPrice())
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

    public OrderResponse updateStatus(UUID orderId, OrderStatus status) {
        Order order = getOrThrow(orderId);
        order.setStatus(status);
        OrderResponse response = toResponse(order);
        if (order.getCustomer() != null) {
            notificationService.notifyCustomer(order.getCustomer().getId(), NotificationType.ORDER_STATUS_CHANGED,
                    "Your order status changed to " + status, orderId.toString());
        }
        return response;
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
                o.getCreatedAt(),
                o.getUpdatedAt()
        );
    }

    private OrderItemResponse toResponse(OrderItem item) {
        String thumbnailUrl = null;
        Product product = item.getProduct();
        if (product != null && !product.getMedia().isEmpty()) {
            thumbnailUrl = s3MediaService.getPresignedUrl(product.getMedia().get(0).getStorageKey());
        }
        return new OrderItemResponse(
                item.getId(),
                product != null ? product.getId() : null,
                item.getProductName(),
                thumbnailUrl,
                item.getQuantity(),
                item.getSelectedColor(),
                item.getSelectedSize(),
                item.getUnitPrice()
        );
    }
}
