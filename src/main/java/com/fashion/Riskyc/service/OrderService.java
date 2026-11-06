package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.LocalizedText;
import com.fashion.Riskyc.dto.request.CreateOrderRequest;
import com.fashion.Riskyc.dto.request.CustomerInfoRequest;
import com.fashion.Riskyc.dto.request.OrderItemRequest;
import com.fashion.Riskyc.dto.response.CustomerInfoResponse;
import com.fashion.Riskyc.dto.response.OrderItemResponse;
import com.fashion.Riskyc.dto.response.OrderResponse;
import com.fashion.Riskyc.dto.response.PaymentPlanResponse;
import com.fashion.Riskyc.entity.*;
import com.fashion.Riskyc.exception.BadRequestException;
import com.fashion.Riskyc.exception.ConflictException;
import com.fashion.Riskyc.exception.ForbiddenException;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
    /** A seller's admins listen on their own shop's channel only — see websocket/StompSubscriptionGuard. */
    private static final String ORDERS_ENTERPRISE_TOPIC = ORDERS_TOPIC + "/enterprise/";

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final ProductVariantService productVariantService;
    private final S3MediaService s3MediaService;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final PushNotificationService pushNotificationService;
    private final SmsService smsService;
    private final TwilioSmsService twilioSmsService;
    private final ConversationService conversationService;
    private final PricingService pricingService;
    private final PaymentAccountService paymentAccountService;

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


    /** Platform staff (no scope) see every order; a seller's scoped admin sees only their own enterprise's orders. */
    @Transactional(readOnly = true)
    public List<OrderResponse> listAll() {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        List<Order> orders = scopeId == null
                ? orderRepository.findAllByOrderByCreatedAtDesc()
                : orderRepository.findByEnterpriseIdOrderByCreatedAtDesc(scopeId);
        return orders.stream().map(this::toResponse).toList();
    }

    /** Online orders that finished the flow (PACKAGED), shown on the POS as completed sales — same enterprise scoping as listAll(). */
    @Transactional(readOnly = true)
    public List<OrderResponse> listCompletedForPos() {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        List<OrderStatus> statuses = List.of(OrderStatus.PACKAGED);
        // Riskyc's till counts only Riskyc's own online sales — every seller's are theirs.
        List<Order> orders = scopeId == null
                ? platformOrders(orderRepository.findByStatusInOrderByCreatedAtDesc(statuses))
                : orderRepository.findByEnterpriseIdAndStatusInOrderByCreatedAtDesc(scopeId, statuses);
        return orders.stream().map(this::toResponse).toList();
    }

    /**
     * The Packing queue — validated orders waiting to be packed, orders
     * currently being packed, and orders already packaged — scoped
     * separately from listAll() so a Packing-only role (VIEW_TREATMENT)
     * never needs VIEW_ORDERS just to see its own work. Same enterprise
     * scoping as listAll() above.
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> listPackingQueue() {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        List<OrderStatus> statuses = List.of(OrderStatus.VALIDATED, OrderStatus.PACKAGING, OrderStatus.PACKAGED);
        // Each seller packs its own orders; Riskyc's packers see Riskyc's own store's queue.
        List<Order> orders = scopeId == null
                ? platformOrders(orderRepository.findByStatusInOrderByCreatedAtDesc(statuses))
                : orderRepository.findByEnterpriseIdAndStatusInOrderByCreatedAtDesc(scopeId, statuses);
        return orders.stream().map(this::toResponse).toList();
    }

    /** The sibling orders produced by one checkout (one per seller involved) — public, same "knowing the id" trust model as getById(). */
    @Transactional(readOnly = true)
    public List<OrderResponse> listByCheckoutGroup(UUID checkoutGroupId) {
        return orderRepository.findByCheckoutGroupIdOrderByCreatedAtDesc(checkoutGroupId).stream().map(this::toResponse).toList();
    }

    /** Riskyc's own store's orders (and pre-marketplace ones with no shop). */
    private static List<Order> platformOrders(List<Order> orders) {
        return orders.stream().filter(o -> o.getEnterprise() == null || o.getEnterprise().isPlatformDefault()).toList();
    }

    /**
     * Confirming a payment and packing belong to the shop that was paid. A seller's order is handled by that seller's
     * own admins — Riskyc's staff can follow it, but the money went to the seller's account, so only they can confirm it.
     */
    private void requireHandledBySeller(Order order, String action) {
        Enterprise shop = order.getEnterprise();
        if (shop == null || shop.isPlatformDefault()) return;
        if (CurrentAdmin.enterpriseIdOrNull() == null) {
            throw new ForbiddenException("Only " + shop.getName() + " can " + action + " — this order was paid to their own account.");
        }
    }

    private void requireOwnedOrPlatform(Order order) {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        if (scopeId == null) return;
        if (order.getEnterprise() == null || !scopeId.equals(order.getEnterprise().getId())) {
            throw new ForbiddenException("This order belongs to a different seller.");
        }
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listForCustomer(UUID customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(UUID id) {
        return toResponse(getOrThrow(id));
    }

    /**
     * A cart can span several sellers — this produces one Order per
     * enterprise involved (today, with every product still backfilled onto
     * the single platform-default enterprise, that's always exactly one
     * Order, so existing single-seller behavior is unchanged byte-for-byte).
     * All resulting Orders share a freshly-generated checkoutGroupId so the
     * confirmation screen and tracking page can present them as one purchase.
     */
    /**
     * Lets checkout confirm every line is still in stock BEFORE the customer sends a mobile-money payment —
     * otherwise a sold-out item is only discovered at order creation, after they've already paid.
     * Same product/suspension/stock rules as create(), quantities summed per (product, color, size).
     */
    @Transactional(readOnly = true)
    public void checkAvailability(List<OrderItemRequest> items) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        Map<String, Product> products = new LinkedHashMap<>();
        for (OrderItemRequest itemReq : items) {
            Product product = productRepository.findById(itemReq.productId())
                    .orElseThrow(() -> new BadRequestException("Product not found: " + itemReq.productId()));
            if (product.getEnterprise() != null && product.getEnterprise().getStatus() == EnterpriseStatus.SUSPENDED) {
                throw new BadRequestException("This product is no longer available: " + product.getName());
            }
            String key = product.getId() + "::" + orEmpty(itemReq.selectedColor()) + "::" + orEmpty(itemReq.selectedSize());
            totals.merge(key, itemReq.quantity(), Integer::sum);
            products.putIfAbsent(key, product);
        }
        totals.forEach((key, quantity) -> {
            String[] parts = key.split("::", -1);
            Product product = products.get(key);
            try {
                productVariantService.requireAvailable(product, parts[1], parts[2], quantity);
            } catch (ConflictException e) {
                throw new ConflictException(product.getName() + ": " + e.getMessage());
            }
        });
    }

    /** Each line's product and the price the customer pays for it, bulk pricing applied per product. */
    private record PricedCart(List<Product> productsByIndex, BigDecimal[] lineTotals) {}

    /**
     * What checkout shows before the customer pays: for each shop in the cart, the amount to send and where to send it
     * (that shop's own dial codes, amount filled in). Same stock check as check-availability and the same prices
     * create() will charge, so what's shown is exactly what each order will cost.
     */
    @Transactional(readOnly = true)
    public PaymentPlanResponse paymentPlan(List<OrderItemRequest> items) {
        checkAvailability(items);
        PricedCart priced = priceCart(items);
        Map<UUID, BigDecimal> amounts = new LinkedHashMap<>();
        Map<UUID, Enterprise> shops = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            Enterprise shop = priced.productsByIndex().get(i).getEnterprise();
            UUID key = shop != null ? shop.getId() : null;
            amounts.merge(key, priced.lineTotals()[i], BigDecimal::add);
            shops.putIfAbsent(key, shop);
        }
        List<PaymentPlanResponse.Seller> sellers = new ArrayList<>(amounts.size());
        amounts.forEach((key, amount) -> {
            Enterprise shop = shops.get(key);
            List<PaymentPlanResponse.Method> methods = paymentAccountService.accountsFor(shop).stream()
                    .map(a -> new PaymentPlanResponse.Method(a.method(), a.codeFor(amount), a.accountName()))
                    .toList();
            sellers.add(new PaymentPlanResponse.Seller(key, shop != null ? shop.getName() : "Riskyc Fashion", amount,
                    !methods.isEmpty(), methods));
        });
        return new PaymentPlanResponse(sellers);
    }

    private PricedCart priceCart(List<OrderItemRequest> items) {
        List<Product> productsByIndex = new ArrayList<>(items.size());
        Map<UUID, List<Integer>> indicesByProduct = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            OrderItemRequest itemReq = items.get(i);
            Product product = productRepository.findById(itemReq.productId())
                    .orElseThrow(() -> new BadRequestException("Product not found: " + itemReq.productId()));
            if (product.getEnterprise() != null && product.getEnterprise().getStatus() == EnterpriseStatus.SUSPENDED) {
                throw new BadRequestException("This product is no longer available: " + product.getName());
            }
            productsByIndex.add(product);
            indicesByProduct.computeIfAbsent(product.getId(), k -> new ArrayList<>()).add(i);
        }
        BigDecimal[] lineTotals = new BigDecimal[items.size()];
        for (List<Integer> indices : indicesByProduct.values()) {
            Product product = productsByIndex.get(indices.get(0));
            List<Integer> quantities = indices.stream().map(i -> items.get(i).quantity()).toList();
            List<BigDecimal> allocated = pricingService.allocateGroupedLineTotals(product.getPrice(), product.getBulkPrices(), quantities);
            for (int j = 0; j < indices.size(); j++) {
                lineTotals[indices.get(j)] = allocated.get(j);
            }
        }
        return new PricedCart(productsByIndex, lineTotals);
    }

    public List<OrderResponse> create(CreateOrderRequest request) {
        Customer customer = null;
        if (request.customerId() != null) {
            customer = customerRepository.findById(request.customerId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Customer", request.customerId()));
        }

        // Resolve every line's product up front, and group line indices by
        // product id — a customer configuring the same product across
        // several photos/sizes/colors (the per-photo picker) still gets bulk
        // pricing on their COMBINED quantity for that product, not on each
        // line in isolation (which would often miss every tier entirely).
        // Grouping by product (not by enterprise) is correct even after the
        // enterprise split below — a single product can only ever belong to
        // one enterprise, so this pass is unaffected by it.
        // A suspended seller's product already sitting in a cart is refused here too (see priceCart).
        PricedCart priced = priceCart(request.items());
        List<Product> productsByIndex = priced.productsByIndex();
        BigDecimal[] lineTotals = priced.lineTotals();
        // Customers pay each shop directly — a shop that hasn't set up its payment numbers can't take orders yet.
        for (Product product : productsByIndex) {
            Enterprise shop = product.getEnterprise();
            if (!paymentAccountService.acceptsPayments(shop)) {
                throw new ConflictException(shop.getName() + " isn't accepting online payments yet — remove its items to continue.");
            }
        }

        // Group item indices by seller (enterprise) — one Order per group.
        // LinkedHashMap preserves first-seen order so the resulting Orders
        // come back in the same order the customer's cart listed sellers in.
        Map<UUID, List<Integer>> indicesByEnterprise = new LinkedHashMap<>();
        for (int i = 0; i < request.items().size(); i++) {
            Enterprise enterprise = productsByIndex.get(i).getEnterprise();
            UUID key = enterprise != null ? enterprise.getId() : null;
            indicesByEnterprise.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }

        String language = "fr".equalsIgnoreCase(request.language()) ? "fr" : "en";
        UUID checkoutGroupId = UUID.randomUUID();
        List<Order> orders = new ArrayList<>(indicesByEnterprise.size());
        for (List<Integer> indices : indicesByEnterprise.values()) {
            Enterprise enterprise = productsByIndex.get(indices.get(0)).getEnterprise();
            Order order = Order.builder()
                    .customer(customer)
                    .status(OrderStatus.PENDING)
                    .customerInfo(toEmbeddable(request.customerInfo()))
                    .total(BigDecimal.ZERO)
                    .language(language)
                    .enterprise(enterprise)
                    .checkoutGroupId(checkoutGroupId)
                    .build();

            BigDecimal subtotal = BigDecimal.ZERO;
            for (int i : indices) {
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

            // Delivery is free on every order — the customer pays exactly the
            // sum of what they selected, nothing added.
            order.setTotal(subtotal);
            orders.add(order);
        }

        // Stock check/decrement — locked in a stable (product, color, size)
        // order across the WHOLE ORIGINAL REQUEST (not per sub-order), so
        // two concurrent checkouts touching overlapping variants always
        // acquire locks in the same sequence regardless of how either one's
        // cart happens to split across sellers (avoids lock-ordering
        // deadlocks). create() is @Transactional, so a failure partway
        // through rolls back everything decremented so far in this same
        // loop along with every Order being built — a multi-item,
        // multi-seller cart either fully succeeds or fully fails, never
        // partially.
        List<Integer> lockOrder = new ArrayList<>();
        for (int i = 0; i < request.items().size(); i++) lockOrder.add(i);
        lockOrder.sort(Comparator.<Integer, UUID>comparing(i -> productsByIndex.get(i).getId())
                .thenComparing(i -> orEmpty(request.items().get(i).selectedColor()))
                .thenComparing(i -> orEmpty(request.items().get(i).selectedSize())));
        for (int i : lockOrder) {
            OrderItemRequest itemReq = request.items().get(i);
            Product product = productsByIndex.get(i);
            ProductVariant variant = productVariantService.resolveForUpdate(product, itemReq.selectedColor(), itemReq.selectedSize());
            productVariantService.decrementStrict(variant, itemReq.quantity());
        }

        List<OrderResponse> responses = new ArrayList<>(orders.size());
        for (Order order : orders) {
            Order saved = orderRepository.saveAndFlush(order);
            notificationService.notifyAdmin(NotificationType.NEW_ORDER,
                    "New order placed for " + formatAmount(saved.getTotal()) + " XAF", saved.getId().toString(),
                    saved.getEnterprise() != null ? saved.getEnterprise().getId() : null);
            responses.add(toResponse(saved));
        }
        return responses;
    }

    public OrderResponse setPaymentMethod(UUID orderId, PaymentMethod method) {
        // Locked like expireIfUnpaid, so an expiry and a payment step can't both win.
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
        requireNotCancelled(order);
        // The customer pays the shop itself — its own number, never Riskyc's on a seller's behalf.
        PaymentAccountService.Account account = paymentAccountService.accountFor(order.getEnterprise(), method)
                .orElseThrow(() -> new ConflictException(paymentAccountService.acceptsPayments(order.getEnterprise())
                        ? shopName(order) + " doesn't accept " + (method == PaymentMethod.ORANGE_MONEY ? "Orange Money" : "MTN Mobile Money") + " — choose another payment method."
                        : shopName(order) + " isn't accepting online payments yet."));
        order.setPaymentMethod(method);
        order.setPaymentCode(account.codeFor(order.getTotal()));
        order.setPaymentAccountName(account.accountName());
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        return toResponse(order);
    }

    public OrderResponse uploadPaymentProof(UUID orderId, MultipartFile file) {
        // Locked like expireIfUnpaid, so an expiry and a payment step can't both win.
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
        requireNotCancelled(order);
        String key = s3MediaService.upload(file, PAYMENT_PROOF_FOLDER + "/" + orderId);
        order.setPaymentScreenshotKey(key);
        order.setStatus(OrderStatus.REVIEWING);
        OrderResponse response = toResponse(order);
        notificationService.notifyAdmin(NotificationType.PAYMENT_PROOF_UPLOADED,
                "Payment proof uploaded for order " + orderId, orderId.toString(),
                order.getEnterprise() != null ? order.getEnterprise().getId() : null);
        pushNotificationService.notifyOrder(orderId,
                new LocalizedText("Payment proof received", "Preuve de paiement reçue"),
                new LocalizedText("We're reviewing your payment — tap to track your order.",
                        "Nous vérifions votre paiement — appuyez pour suivre votre commande."),
                siteUrl + "/track/" + orderId);

        // 1 of 3 order-lifecycle SMS — the customer has now fully completed
        // checkout (payment method chosen + proof uploaded), so this is the
        // real "we've got your order" moment, not raw order creation.
        String customerPhone = order.getCustomerInfo() != null ? order.getCustomerInfo().getPhone() : null;
        sendCustomerSms(customerPhone, new LocalizedText(
                "Riskyc Fashion: your order " + orderId + " was correctly received at Riskyc Fashion store. "
                        + "We are currently reviewing your payment screenshot and you will receive a notification when it is validated. "
                        + "Track your order: " + siteUrl + "/track/" + orderId,
                "Riskyc Fashion : votre commande " + orderId + " a bien été reçue à la boutique Riskyc Fashion. "
                        + "Nous examinons actuellement votre preuve de paiement et vous recevrez une notification dès qu'elle sera validée. "
                        + "Suivez votre commande : " + siteUrl + "/track/" + orderId
        ).forLanguage(order.getLanguage()));

        return response;
    }

    public OrderResponse updateStatus(UUID orderId, OrderStatus status, String reason) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
        requireOwnedOrPlatform(order);
        if (status == OrderStatus.VALIDATED) requireHandledBySeller(order, "confirm this payment");
        requireAllowedTransition(order.getStatus(), status);
        order.setStatus(status);
        order.setStatusChangedByName(CurrentAdmin.nameOrNull());
        order.setStatusChangedAt(Instant.now());
        if (status == OrderStatus.CANCELLED) {
            order.setRejectionReason(reason);
            restoreStock(order);
        }
        OrderResponse response = toResponse(order);
        if (order.getCustomer() != null) {
            notificationService.notifyCustomer(order.getCustomer().getId(), NotificationType.ORDER_STATUS_CHANGED,
                    new LocalizedText("Your order status changed to " + status, "Le statut de votre commande a changé : " + status)
                            .forLanguage(order.getLanguage()),
                    orderId.toString());
        }
        String trackingUrl = siteUrl + "/track/" + orderId;
        String customerPhone = order.getCustomerInfo() != null ? order.getCustomerInfo().getPhone() : null;
        if (status == OrderStatus.VALIDATED) {
            pushNotificationService.notifyOrder(orderId,
                    new LocalizedText("Payment confirmed!", "Paiement confirmé !"),
                    new LocalizedText("Your payment has been validated — tap to track your order.",
                            "Votre paiement a été validé — appuyez pour suivre votre commande."),
                    trackingUrl);
            // 2 of 3 order-lifecycle SMS — sent alongside push, not instead
            // of it, since it reaches the customer even without push enabled.
            sendCustomerSms(customerPhone, new LocalizedText(
                    "Riskyc Fashion: your order " + orderId + " has been validated! Track your order: " + trackingUrl,
                    "Riskyc Fashion : votre commande " + orderId + " a été validée ! Suivez votre commande : " + trackingUrl
            ).forLanguage(order.getLanguage()));
        } else if (status == OrderStatus.CANCELLED) {
            pushNotificationService.notifyOrder(orderId,
                    new LocalizedText("Order rejected", "Commande rejetée"),
                    new LocalizedText("We couldn't validate your payment — tap for details.",
                            "Nous n'avons pas pu valider votre paiement — appuyez pour plus de détails."),
                    trackingUrl);
        }
        broadcastOrder(order, response);
        return response;
    }

    /**
     * Manual status changes from the admin's order screens. Packing/packed only come from start/completePackaging
     * (which record who packed it and when, and what counts it as a sale); once validated an order can only be
     * cancelled; and packing, packed or cancelled orders are final here — a cancelled order's stock has already
     * been returned, so reopening it would sell items that may no longer exist.
     */
    private void requireAllowedTransition(OrderStatus from, OrderStatus to) {
        if (from == to) return;
        if (to == OrderStatus.PACKAGING || to == OrderStatus.PACKAGED) {
            throw new ConflictException("Use Start packaging / Mark as packaged to move an order into packing.");
        }
        switch (from) {
            case CANCELLED -> throw new ConflictException("This order was cancelled and can't be reopened — the customer needs to order again.");
            case PACKAGING, PACKAGED -> throw new ConflictException("This order is already " + from.name().toLowerCase() + " and can't be changed here.");
            case VALIDATED -> {
                if (to != OrderStatus.CANCELLED) {
                    throw new ConflictException("A validated order can only be cancelled.");
                }
            }
            default -> { }
        }
    }

    /** Orders still waiting for the customer to pay — they hold stock until paid or expired. */
    public static final List<OrderStatus> UNPAID_STATUSES = List.of(OrderStatus.PENDING, OrderStatus.AWAITING_PAYMENT);

    /**
     * Cancels one order that was never paid and gives its stock back. Both apps create, choose the payment method and
     * upload the proof in one step, so an order left here is a checkout that failed or was abandoned part-way — without
     * this its items stayed reserved forever. Re-checked under the row lock: a proof that arrived meanwhile wins.
     * Payment itself is untouched; an order with a proof (REVIEWING) is never expired.
     *
     * @return true if the order was expired
     */
    public boolean expireIfUnpaid(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || !UNPAID_STATUSES.contains(order.getStatus()) || order.getPaymentScreenshotKey() != null) {
            return false;
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setRejectionReason("Payment wasn't completed in time, so the order expired and its items went back on sale.");
        order.setStatusChangedByName("Automatic expiry");
        order.setStatusChangedAt(Instant.now());
        restoreStock(order);
        broadcastOrder(order, toResponse(order));
        return true;
    }

    /** A cancelled order (expired or rejected) has already given its stock back, so it can't be paid any more. */
    private void requireNotCancelled(Order order) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ConflictException("This order was cancelled, so it can't be paid any more — please place the order again.");
        }
    }

    private static String shopName(Order order) {
        return order.getEnterprise() != null ? order.getEnterprise().getName() : "This shop";
    }

    /** Puts a cancelled order's items back in stock — the same stock the till sells from. */
    private void restoreStock(Order order) {
        for (OrderItem item : order.getItems()) {
            if (item.getProduct() == null) continue; // product deleted since — nothing to restore against
            ProductVariant variant = productVariantService.resolveForUpdate(item.getProduct(), item.getSelectedColor(), item.getSelectedSize());
            productVariantService.restock(variant, item.getQuantity());
        }
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
        requireOwnedOrPlatform(order);
        requireHandledBySeller(order, "pack this order");
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
        broadcastOrder(order, response);
        pushNotificationService.notifyOrder(orderId,
                new LocalizedText("Your order is being packaged", "Votre commande est en cours d'emballage"),
                new LocalizedText("We've started packaging your order — tap to track it.",
                        "Nous avons commencé à emballer votre commande — appuyez pour la suivre."),
                siteUrl + "/track/" + orderId);
        return response;
    }

    /** Marks an in-progress packaging job as done. */
    public OrderResponse completePackaging(UUID orderId) {
        Order order = getOrThrow(orderId);
        requireOwnedOrPlatform(order);
        requireHandledBySeller(order, "pack this order");
        if (order.getStatus() != OrderStatus.PACKAGING) {
            throw new ConflictException("Order isn't currently being packaged (status is " + order.getStatus() + ")");
        }
        order.setStatus(OrderStatus.PACKAGED);
        order.setPackagingCompletedByName(CurrentAdmin.nameOrNull());
        order.setPackagingCompletedById(CurrentAdmin.idOrNull());
        order.setPackagingCompletedAt(Instant.now());
        OrderResponse response = toResponse(order);
        broadcastOrder(order, response);
        // Deliberately worded differently from ConversationService's
        // sendPackagingConfirmation push below — an admin normally finishes
        // packaging and then sends the delivery-details/photo message
        // moments later, and two near-identical "packaged!" notifications
        // back to back reads as a bug rather than two distinct updates.
        pushNotificationService.notifyOrder(orderId,
                new LocalizedText("Your order is packaged!", "Votre commande est emballée !"),
                new LocalizedText("We'll share your delivery details shortly — tap to track it.",
                        "Nous partagerons vos informations de livraison sous peu — appuyez pour suivre votre commande."),
                siteUrl + "/track/" + orderId);

        // 3 of 3 order-lifecycle SMS.
        String customerPhone = order.getCustomerInfo() != null ? order.getCustomerInfo().getPhone() : null;
        sendCustomerSms(customerPhone, new LocalizedText(
                "Riskyc Fashion: your order " + orderId + " has been packaged and is ready! "
                        + "Track your order: " + siteUrl + "/track/" + orderId,
                "Riskyc Fashion : votre commande " + orderId + " a été emballée et est prête ! "
                        + "Suivez votre commande : " + siteUrl + "/track/" + orderId
        ).forLanguage(order.getLanguage()));

        return response;
    }

    /**
     * Links a guest order to a customer account after the fact — reached
     * from the "create an account to track this order" prompt on the
     * confirmation screen or tracking page, once that signup/login actually
     * succeeds. Never overwrites an existing attachment (a guest order can
     * only ever be claimed once), and silently no-ops rather than throwing
     * if it's already attached, since a failed/duplicate call from the
     * frontend shouldn't surface as an error to someone who just signed up.
     */
    public OrderResponse attachCustomer(UUID orderId, UUID customerId) {
        Order order = getOrThrow(orderId);
        if (order.getCustomer() == null) {
            Customer customer = customerRepository.findById(customerId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Customer", customerId));
            order.setCustomer(customer);
        }
        return toResponse(order);
    }

    private String orEmpty(String s) {
        return s != null ? s : "";
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
                o.getEnterprise() != null ? o.getEnterprise().getId() : null,
                o.getEnterprise() != null ? o.getEnterprise().getName() : null,
                o.getCheckoutGroupId(),
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

    /**
     * Platform staff get every order on /topic/orders; the order's own shop also gets it on its channel. Sent after the
     * transaction commits, so dashboards never show a change that was rolled back.
     */
    private void broadcastOrder(Order order, OrderResponse response) {
        UUID enterpriseId = order.getEnterprise() != null ? order.getEnterprise().getId() : null;
        Runnable send = () -> {
            messagingTemplate.convertAndSend(ORDERS_TOPIC, response);
            if (enterpriseId != null) {
                messagingTemplate.convertAndSend(ORDERS_ENTERPRISE_TOPIC + enterpriseId, response);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
