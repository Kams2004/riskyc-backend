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
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final SmsService smsService;
    private final TwilioSmsService twilioSmsService;
    private final ConversationService conversationService;

    /** Twilio is preferred over Orange when both happen to be configured — never both, so having a second provider set up doesn't double the SMS cost of an order. */
    private void sendCustomerSms(String phone, String message) {
        if (twilioSmsService.isConfigured()) {
            twilioSmsService.send(phone, message);
        } else {
            smsService.send(phone, message);
        }
    }

    @Value("${app.site.url}")
    private String siteUrl;

    @Value("${app.payment.orange-money-code}")
    private String orangeMoneyCode;

    @Value("${app.payment.orange-money-name}")
    private String orangeMoneyName;

    @Value("${app.payment.mobile-money-code}")
    private String mobileMoneyCode;

    @Value("${app.payment.mobile-money-name}")
    private String mobileMoneyName;

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

        // Resolve every line's product up front, and group line indices by
        // product id — a customer configuring the same product across
        // several photos/sizes/colors (the per-photo picker) still gets bulk
        // pricing on their COMBINED quantity for that product, not on each
        // line in isolation (which would often miss every tier entirely).
        List<Product> productsByIndex = new ArrayList<>(request.items().size());
        Map<UUID, List<Integer>> indicesByProduct = new LinkedHashMap<>();
        for (int i = 0; i < request.items().size(); i++) {
            OrderItemRequest itemReq = request.items().get(i);
            Product product = productRepository.findById(itemReq.productId())
                    .orElseThrow(() -> new BadRequestException("Product not found: " + itemReq.productId()));
            productsByIndex.add(product);
            indicesByProduct.computeIfAbsent(product.getId(), k -> new ArrayList<>()).add(i);
        }

        BigDecimal[] lineTotals = new BigDecimal[request.items().size()];
        for (List<Integer> indices : indicesByProduct.values()) {
            Product product = productsByIndex.get(indices.get(0));
            List<Integer> quantities = indices.stream().map(i -> request.items().get(i).quantity()).toList();
            List<BigDecimal> allocated = allocateGroupedLineTotals(product.getPrice(), product.getBulkPrices(), quantities);
            for (int j = 0; j < indices.size(); j++) {
                lineTotals[indices.get(j)] = allocated.get(j);
            }
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        for (int i = 0; i < request.items().size(); i++) {
            OrderItemRequest itemReq = request.items().get(i);
            Product product = productsByIndex.get(i);
            BigDecimal lineTotal = lineTotals[i];
            subtotal = subtotal.add(lineTotal);
            BigDecimal effectiveUnitPrice = lineTotal.divide(BigDecimal.valueOf(itemReq.quantity()), 2, RoundingMode.HALF_UP);

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
        String template = method == PaymentMethod.ORANGE_MONEY ? orangeMoneyCode : mobileMoneyCode;
        String amount = order.getTotal().setScale(0, java.math.RoundingMode.DOWN).toPlainString();
        order.setPaymentCode(String.format(template, amount));
        order.setPaymentAccountName(method == PaymentMethod.ORANGE_MONEY ? orangeMoneyName : mobileMoneyName);
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
        pushNotificationService.notifyOrder(orderId, "Payment proof received",
                "We're reviewing your payment — tap to track your order.", siteUrl + "/track/" + orderId);

        // 1 of 3 order-lifecycle SMS — the customer has now fully completed
        // checkout (payment method chosen + proof uploaded), so this is the
        // real "we've got your order" moment, not raw order creation.
        String customerPhone = order.getCustomerInfo() != null ? order.getCustomerInfo().getPhone() : null;
        sendCustomerSms(customerPhone, "Riskyc Fashion: your order " + orderId + " was correctly received at Riskyc Fashion store. "
                + "We are currently reviewing your payment screenshot and you will receive a notification when it is validated. "
                + "Track your order: " + siteUrl + "/track/" + orderId);

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
        String customerPhone = order.getCustomerInfo() != null ? order.getCustomerInfo().getPhone() : null;
        if (status == OrderStatus.VALIDATED) {
            pushNotificationService.notifyOrder(orderId, "Payment confirmed!",
                    "Your payment has been validated — tap to track your order.", trackingUrl);
            // 2 of 3 order-lifecycle SMS — sent alongside push, not instead
            // of it, since it reaches the customer even without push enabled.
            sendCustomerSms(customerPhone, "Riskyc Fashion: your order " + orderId + " has been validated! "
                    + "Track your order: " + trackingUrl);
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
        // Locked, not a plain findById — without the lock, two admins
        // tapping "Start Packaging" at the same moment can both read
        // VALIDATED before either commits, and both would proceed instead
        // of the second one correctly losing the race below.
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
        if (order.getStatus() != OrderStatus.VALIDATED) {
            if (order.getStatus() == OrderStatus.PACKAGING) {
                throw new ConflictException("Already being packaged by " + order.getPackagingStartedByName());
            }
            throw new ConflictException("Order isn't in a packageable state (currently " + order.getStatus() + ")");
        }
        order.setStatus(OrderStatus.PACKAGING);
        order.setPackagingStartedByName(CurrentAdmin.nameOrNull());
        order.setPackagingStartedById(CurrentAdmin.idOrNull());
        order.setPackagingStartedAt(Instant.now());
        OrderResponse response = toResponse(order);
        messagingTemplate.convertAndSend(ORDERS_TOPIC, response);
        pushNotificationService.notifyOrder(orderId, "Your order is being packaged",
                "We've started packaging your order — tap to track it.", siteUrl + "/track/" + orderId);
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
        order.setPackagingCompletedById(CurrentAdmin.idOrNull());
        order.setPackagingCompletedAt(Instant.now());
        OrderResponse response = toResponse(order);
        messagingTemplate.convertAndSend(ORDERS_TOPIC, response);
        pushNotificationService.notifyOrder(orderId, "Your order has been packaged!",
                "Your order is packaged and ready — tap to see the details.", siteUrl + "/track/" + orderId);

        // 3 of 3 order-lifecycle SMS.
        String customerPhone = order.getCustomerInfo() != null ? order.getCustomerInfo().getPhone() : null;
        sendCustomerSms(customerPhone, "Riskyc Fashion: your order " + orderId + " has been packaged and is ready! "
                + "Track your order: " + siteUrl + "/track/" + orderId);

        return response;
    }

    /**
     * Splits the bulk/grouped-pricing tiers for a product across a list of
     * order lines requesting that same product (e.g. one line per photo the
     * per-photo picker was used to configure), pricing all of them together
     * as a single continuous quantity rather than each line in isolation —
     * 2 + 3 + 5 units of the same product across three lines gets a
     * 10-for-20,000 tier exactly as if they'd been one line of 10, instead
     * of each line individually falling short of the tier and paying full
     * price. A later order adding one more unit (making 11) correctly costs
     * the 10-unit tier plus one unit at the regular price, not a re-priced
     * 11-unit blend.
     *
     * Tiers apply greedily by largest-quantity first (mirrors lib/pricing.ts
     * on the frontend, which shows the customer this same figure before
     * they submit): as many of the biggest tier as fit, then the next, and
     * so on, with any leftover charged at the regular unit price — never a
     * partial/prorated tier rate. The resulting tier "chunks" are then
     * walked in the order the lines were given, splitting a chunk across a
     * line boundary when a line's quantity doesn't line up evenly with it,
     * so `quantities.size() == 1` naturally reduces to pricing one plain line.
     */
    private List<BigDecimal> allocateGroupedLineTotals(BigDecimal unitPrice, List<BulkPriceTier> bulkPrices, List<Integer> quantities) {
        int totalQuantity = quantities.stream().mapToInt(Integer::intValue).sum();
        if (totalQuantity <= 0) return quantities.stream().map(q -> BigDecimal.ZERO).toList();

        List<BulkPriceTier> tiers = bulkPrices.stream()
                .filter(t -> t.getQuantity() != null && t.getPrice() != null && t.getQuantity() > 0 && t.getPrice().signum() > 0)
                .sorted(java.util.Comparator.comparingInt(BulkPriceTier::getQuantity).reversed())
                .toList();

        record Chunk(int units, BigDecimal totalPrice) {}
        List<Chunk> chunks = new ArrayList<>();
        int remaining = totalQuantity;
        for (BulkPriceTier tier : tiers) {
            if (remaining >= tier.getQuantity()) {
                int count = remaining / tier.getQuantity();
                for (int i = 0; i < count; i++) {
                    chunks.add(new Chunk(tier.getQuantity(), tier.getPrice()));
                }
                remaining -= count * tier.getQuantity();
            }
        }
        if (remaining > 0) {
            chunks.add(new Chunk(remaining, unitPrice.multiply(BigDecimal.valueOf(remaining))));
        }

        List<BigDecimal> lineTotals = new ArrayList<>(quantities.size());
        int chunkIndex = 0;
        int unitsUsedInChunk = 0;
        for (int quantity : quantities) {
            BigDecimal lineTotal = BigDecimal.ZERO;
            int remainingForLine = quantity;
            while (remainingForLine > 0) {
                Chunk chunk = chunks.get(chunkIndex);
                int unitsLeftInChunk = chunk.units() - unitsUsedInChunk;
                int take = Math.min(remainingForLine, unitsLeftInChunk);
                BigDecimal perUnit = chunk.totalPrice().divide(BigDecimal.valueOf(chunk.units()), 4, RoundingMode.HALF_UP);
                lineTotal = lineTotal.add(perUnit.multiply(BigDecimal.valueOf(take)));
                unitsUsedInChunk += take;
                remainingForLine -= take;
                if (unitsUsedInChunk >= chunk.units()) {
                    chunkIndex++;
                    unitsUsedInChunk = 0;
                }
            }
            lineTotals.add(lineTotal.setScale(2, RoundingMode.HALF_UP));
        }
        return lineTotals;
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
                o.getPaymentAccountName(),
                paymentScreenshotUrl,
                o.getStatusChangedByName(),
                o.getStatusChangedAt(),
                o.getRejectionReason(),
                o.getPackagingStartedByName(),
                o.getPackagingStartedById(),
                o.getPackagingStartedAt(),
                o.getPackagingCompletedByName(),
                o.getPackagingCompletedById(),
                o.getPackagingCompletedAt(),
                conversationService.getPackagingConfirmationMessage(o.getId()),
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
