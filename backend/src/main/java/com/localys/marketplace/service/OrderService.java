package com.localys.marketplace.service;

import com.localys.marketplace.dto.OrderCreateRequest;
import com.localys.marketplace.dto.OrderDto;
import com.localys.marketplace.dto.OrderResponse;
import com.localys.marketplace.model.Address;
import com.localys.marketplace.model.Cart;
import com.localys.marketplace.model.CartItem;
import com.localys.marketplace.model.Order;
import com.localys.marketplace.model.OrderItem;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.ProductImage;
import com.localys.marketplace.model.ProductRemovalEvent;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.enums.CartStatus;
import com.localys.marketplace.model.enums.ListingRemovalReason;
import com.localys.marketplace.model.enums.OrderStatus;
import com.localys.marketplace.model.enums.ModerationStatus;
import com.localys.marketplace.model.enums.PaymentStatus;
import com.localys.marketplace.model.enums.ShippingMethod;
import com.localys.marketplace.repository.AddressRepository;
import com.localys.marketplace.repository.CartItemRepository;
import com.localys.marketplace.repository.CartRepository;
import com.localys.marketplace.repository.OrderItemRepository;
import com.localys.marketplace.repository.OrderRepository;
import com.localys.marketplace.repository.PaymentRepository;
import com.localys.marketplace.repository.ProductRemovalEventRepository;
import com.localys.marketplace.repository.ProductRepository;
import com.localys.marketplace.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Order lifecycle:
 * <ol>
 *     <li>{@link #createOrder}: stock is reserved (deducted under a row lock) so two buyers can
 *     never pay for the same last item. {@link Order#isStockDeducted()} marks the reservation.</li>
 *     <li>{@link #markPaid}: payment confirmed, listings whose stock reached zero are closed as sold.</li>
 *     <li>{@link #cancelUnpaid}: payment expired/failed, the reservation is released back to stock.</li>
 * </ol>
 * Every state change locks the order row so concurrent webhooks and the cleanup job cannot race.
 */
@Service
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private static final Set<OrderStatus> PAID_STATES =
            EnumSet.of(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.REFUNDED);
    private static final Set<OrderStatus> AWAITING_PAYMENT_STATES =
            EnumSet.of(OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_CONFIRMING);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final ProductRemovalEventRepository productRemovalEventRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;
    private final ReviewService reviewService;

    @PersistenceContext
    private EntityManager entityManager;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            UserRepository userRepository,
            AddressRepository addressRepository,
            ProductRemovalEventRepository productRemovalEventRepository,
            ProductRepository productRepository,
            PaymentRepository paymentRepository,
            NotificationService notificationService,
            ReviewService reviewService
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.productRemovalEventRepository = productRemovalEventRepository;
        this.productRepository = productRepository;
        this.paymentRepository = paymentRepository;
        this.notificationService = notificationService;
        this.reviewService = reviewService;
    }

    @Transactional
    public OrderResponse createOrder(Long userId, OrderCreateRequest request) {
        if (request.productIds() == null || request.productIds().isEmpty()) {
            throw new IllegalArgumentException("No items selected");
        }
        if (request.addressId() == null) {
            throw new IllegalArgumentException("Address is required");
        }
        ShippingMethod shippingMethod = ShippingMethod.from(request.shippingMethod())
                .orElseThrow(() -> new IllegalArgumentException("Invalid shipping method"));
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Address address = addressRepository.findByIdAndUser_Id(request.addressId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("Address not found"));

        Cart cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found"));

        List<CartItem> selectedItems = cart.getItems().stream()
                .filter(item -> request.productIds().contains(item.getProduct().getId()))
                .toList();
        if (selectedItems.isEmpty()) {
            throw new IllegalArgumentException("No items selected");
        }
        if (selectedItems.stream().map(CartItem::getCurrency).distinct().count() > 1) {
            throw new IllegalArgumentException("Items with different currencies cannot be ordered together");
        }

        // Lock the products before reading their stock so concurrent checkouts are serialized.
        Map<Long, Product> lockedProducts = lockProducts(
                selectedItems.stream().map(item -> item.getProduct().getId()).toList());

        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.PAYMENT_PENDING);
        order.setShippingAddress(address);
        order.setBillingAddress(address);
        order.setShippingMethod(shippingMethod.name());
        order.setPaymentMethod(request.paymentMethod());
        BigDecimal shippingPrice = shippingMethod.price();
        order.setShippingPrice(shippingPrice);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem cartItem : selectedItems) {
            Product product = lockedProducts.get(cartItem.getProduct().getId());
            if (product == null
                    || !product.isActive()
                    || product.getModerationStatus() != ModerationStatus.APPROVED
                    || product.getExpiresAt() == null
                    || !product.getExpiresAt().isAfter(OffsetDateTime.now())) {
                throw new IllegalArgumentException("Product not found");
            }
            if (product.getVendor() != null
                    && product.getVendor().getUser() != null
                    && userId.equals(product.getVendor().getUser().getId())) {
                throw new IllegalArgumentException("Cannot buy own product");
            }
            if (cartItem.getQuantity() <= 0 || cartItem.getQuantity() > product.getStockQty()) {
                throw new IllegalArgumentException("Product out of stock");
            }
            product.setStockQty(product.getStockQty() - cartItem.getQuantity());

            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProduct(product);
            item.setVendor(product.getVendor());
            item.setProductNameSnapshot(product.getName());
            item.setUnitPriceSnapshot(cartItem.getUnitPriceSnapshot());
            item.setQuantity(cartItem.getQuantity());
            BigDecimal lineTotal = cartItem.getUnitPriceSnapshot()
                    .multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            item.setLineTotal(lineTotal);
            item.setCurrency(cartItem.getCurrency());
            order.getItems().add(item);
            subtotal = subtotal.add(lineTotal);
        }

        order.setTotalAmount(subtotal.add(shippingPrice));
        order.setCurrency(selectedItems.get(0).getCurrency());
        order.setStockDeducted(true);
        Order saved = orderRepository.save(order);
        orderItemRepository.saveAll(saved.getItems());

        selectedItems.forEach(item -> {
            cartItemRepository.delete(item);
            cart.getItems().remove(item);
        });

        return toResponse(saved, subtotal);
    }

    /**
     * Confirms payment for an order and closes listings that are now sold out.
     * Idempotent: Stripe may deliver several "paid" events for the same order.
     */
    @Transactional
    public void markPaid(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || PAID_STATES.contains(order.getStatus())) {
            return;
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            // Money was captured after we released the reservation; take the stock again
            // (best effort) and flag it so the order can be reviewed manually.
            logger.error("Payment succeeded for cancelled order {}; re-reserving stock, manual review required", orderId);
        }
        Map<Long, Product> locked = lockProducts(order);
        if (!order.isStockDeducted()) {
            adjustStock(order, locked, -1);
            order.setStockDeducted(true);
        }
        order.setStatus(OrderStatus.PAID);
        for (Product product : locked.values()) {
            if (product.getStockQty() <= 0 && product.isActive()) {
                closeAsSold(product, order.getUser());
            }
        }
        orderRepository.save(order);

        notificationService.createOrderPaidNotification(order.getUser(), order.getId());
        for (OrderItem item : order.getItems()) {
            if (item.getVendor() != null && item.getVendor().getUser() != null) {
                notificationService.createOrderSoldNotification(item.getVendor().getUser(), item.getProductNameSnapshot());
            }
        }
    }

    /**
     * Async payment methods (e.g. SEPA debit) complete the checkout before the money arrives.
     * The reservation is kept until the final success/failure event.
     */
    @Transactional
    public void markPaymentConfirming(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            return;
        }
        order.setStatus(OrderStatus.PAYMENT_CONFIRMING);
        orderRepository.save(order);
    }

    /**
     * Cancels an order that was never paid and releases its stock reservation.
     * No-op for orders that are already paid or cancelled.
     */
    @Transactional
    public void cancelUnpaid(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || !AWAITING_PAYMENT_STATES.contains(order.getStatus())) {
            return;
        }
        if (order.isStockDeducted()) {
            adjustStock(order, lockProducts(order), +1);
            order.setStockDeducted(false);
        }
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        restoreCartItems(order);
        // Keep a final status the webhook already recorded (e.g. FAILED); only close open payments.
        paymentRepository.findByOrder_Id(orderId)
                .filter(payment -> payment.getStatus() == PaymentStatus.REQUIRES_PAYMENT
                        || payment.getStatus() == PaymentStatus.PROCESSING)
                .ifPresent(payment -> {
                    payment.setStatus(PaymentStatus.CANCELLED);
                    paymentRepository.save(payment);
                });
    }

    @Transactional
    public List<OrderDto> listForBuyer(Long userId) {
        return orderRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(order -> toDto(order, order.getItems(), null))
                .toList();
    }

    @Transactional
    public OrderDto getForBuyer(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUser_Id(orderId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        return toDto(order, order.getItems(), null);
    }

    /**
     * Paid orders containing the seller's listings. Each order only exposes the seller's own
     * items; unpaid orders are hidden so buyer addresses are not shared before payment.
     */
    @Transactional
    public List<OrderDto> listSales(Long sellerUserId) {
        Map<Order, List<OrderItem>> itemsByOrder = orderItemRepository.findSoldItems(sellerUserId, PAID_STATES).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrder, LinkedHashMap::new, Collectors.toList()));
        return itemsByOrder.entrySet().stream()
                .map(entry -> toDto(entry.getKey(), entry.getValue(), sellerUserId))
                .toList();
    }

    /**
     * Marks the seller's items of a paid order as shipped. Each seller ships their own items;
     * the order becomes SHIPPED once every item is on its way.
     */
    @Transactional
    public OrderDto shipItems(Long sellerUserId, Long orderId, String trackingNumber) {
        String tracking = trackingNumber == null ? "" : trackingNumber.trim();
        if (tracking.isEmpty() || tracking.length() > 100) {
            throw new IllegalArgumentException("Tracking number is required");
        }
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        List<OrderItem> sellerItems = order.getItems().stream()
                .filter(item -> isSoldBy(item, sellerUserId))
                .toList();
        if (sellerItems.isEmpty()) {
            throw new IllegalArgumentException("Order not found");
        }
        if (order.getStatus() != OrderStatus.PAID) {
            throw new IllegalArgumentException("Order cannot be shipped in its current state");
        }
        List<OrderItem> toShip = sellerItems.stream()
                .filter(item -> item.getShippedAt() == null)
                .toList();
        if (toShip.isEmpty()) {
            throw new IllegalArgumentException("Order cannot be shipped: items already shipped");
        }
        OffsetDateTime now = OffsetDateTime.now();
        for (OrderItem item : toShip) {
            item.setTrackingNumber(tracking);
            item.setShippedAt(now);
        }
        if (order.getItems().stream().allMatch(item -> item.getShippedAt() != null)) {
            order.setStatus(OrderStatus.SHIPPED);
        }
        orderRepository.save(order);
        notificationService.createOrderShippedNotification(
                order.getUser(),
                order.getId(),
                joinNames(toShip),
                tracking
        );
        return toDto(order, sellerItems, sellerUserId);
    }

    /**
     * The buyer confirms receiving everything shipped so far. The order becomes DELIVERED
     * once every item has been received.
     */
    @Transactional
    public OrderDto confirmDelivery(Long buyerId, Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .filter(found -> found.getUser().getId().equals(buyerId))
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        List<OrderItem> toConfirm = order.getItems().stream()
                .filter(item -> item.getShippedAt() != null && item.getDeliveredAt() == null)
                .toList();
        if (toConfirm.isEmpty()) {
            throw new IllegalArgumentException("Order cannot be confirmed: nothing shipped yet");
        }
        OffsetDateTime now = OffsetDateTime.now();
        toConfirm.forEach(item -> item.setDeliveredAt(now));
        if (order.getItems().stream().allMatch(item -> item.getDeliveredAt() != null)) {
            order.setStatus(OrderStatus.DELIVERED);
        }
        orderRepository.save(order);
        for (OrderItem item : toConfirm) {
            if (item.getVendor() != null && item.getVendor().getUser() != null) {
                notificationService.createOrderDeliveredNotification(item.getVendor().getUser(), item.getProductNameSnapshot());
            }
        }
        return toDto(order, order.getItems(), null);
    }

    private boolean isSoldBy(OrderItem item, Long sellerUserId) {
        return item.getVendor() != null
                && item.getVendor().getUser() != null
                && sellerUserId.equals(item.getVendor().getUser().getId());
    }

    private String joinNames(List<OrderItem> items) {
        return items.stream()
                .map(OrderItem::getProductNameSnapshot)
                .collect(Collectors.joining(", "));
    }

    /**
     * Puts the items of an unpaid order back into the buyer's cart so an abandoned payment
     * does not silently empty it. Items that are no longer available are skipped.
     */
    private void restoreCartItems(Order order) {
        UserEntity buyer = order.getUser();
        Cart cart = cartRepository.findByUserIdAndStatus(buyer.getId(), CartStatus.ACTIVE)
                .orElseGet(() -> {
                    Cart created = new Cart();
                    created.setUser(buyer);
                    created.setStatus(CartStatus.ACTIVE);
                    return cartRepository.save(created);
                });
        OffsetDateTime now = OffsetDateTime.now();
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            boolean available = product != null
                    && product.isActive()
                    && product.getModerationStatus() == ModerationStatus.APPROVED
                    && product.getExpiresAt() != null
                    && product.getExpiresAt().isAfter(now)
                    && product.getStockQty() > 0;
            boolean alreadyInCart = available && cart.getItems().stream()
                    .anyMatch(cartItem -> cartItem.getProduct().getId().equals(product.getId()));
            if (!available || alreadyInCart) {
                continue;
            }
            CartItem cartItem = new CartItem();
            cartItem.setCart(cart);
            cartItem.setProduct(product);
            cartItem.setQuantity(Math.min(item.getQuantity(), product.getStockQty()));
            cartItem.setUnitPriceSnapshot(product.getPrice());
            cartItem.setCurrency(product.getCurrency());
            cart.getItems().add(cartItem);
        }
        cartRepository.save(cart);
    }

    /**
     * @param sellerUserId the seller viewing their sale, or null for the buyer's view
     */
    private OrderDto toDto(Order order, List<OrderItem> items, Long sellerUserId) {
        boolean sellerView = sellerUserId != null;
        Long viewerId = sellerView ? sellerUserId : order.getUser().getId();
        Map<Long, Integer> ratings = reviewService.ratingsGivenBy(
                viewerId, items.stream().map(OrderItem::getId).toList());
        List<OrderDto.OrderItemDto> itemDtos = items.stream()
                .map(item -> new OrderDto.OrderItemDto(
                        item.getId(),
                        item.getProduct().getId(),
                        item.getProductNameSnapshot(),
                        firstImageUrl(item.getProduct()),
                        item.getQuantity(),
                        item.getUnitPriceSnapshot(),
                        item.getLineTotal(),
                        item.getTrackingNumber(),
                        item.getShippedAt(),
                        item.getDeliveredAt(),
                        ratings.get(item.getId())
                ))
                .toList();
        BigDecimal subtotal = items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // A seller only gets the shipping fee when they ship the whole order.
        BigDecimal shippingPrice = sellerView && items.size() != order.getItems().size()
                ? BigDecimal.ZERO
                : order.getShippingPrice();
        Address address = order.getShippingAddress();
        OrderDto.ShippingAddressDto addressDto = address == null ? null : new OrderDto.ShippingAddressDto(
                address.getFullName(),
                address.getPhone(),
                address.getLine1(),
                address.getLine2(),
                address.getPostalCode(),
                address.getCity(),
                address.getCountry()
        );
        return new OrderDto(
                order.getId(),
                order.getStatus().name(),
                subtotal,
                shippingPrice,
                subtotal.add(shippingPrice == null ? BigDecimal.ZERO : shippingPrice),
                order.getCurrency(),
                order.getShippingMethod(),
                order.getCreatedAt(),
                sellerView ? displayName(order.getUser()) : null,
                sellerView ? order.getUser().getId() : null,
                addressDto,
                itemDtos
        );
    }

    private String firstImageUrl(Product product) {
        return product.getImages().stream()
                .sorted(Comparator.comparingInt(ProductImage::getSortOrder))
                .map(ProductImage::getUrl)
                .findFirst()
                .orElse(null);
    }

    private String displayName(UserEntity user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }
        return user.getUsername();
    }

    private Map<Long, Product> lockProducts(Order order) {
        List<Long> productIds = order.getItems().stream()
                .map(item -> item.getProduct().getId())
                .distinct()
                .toList();
        return lockProducts(productIds);
    }

    private Map<Long, Product> lockProducts(List<Long> productIds) {
        List<Product> locked = productRepository.findAllByIdForUpdate(productIds);
        // The query takes the row locks, but Hibernate keeps the state of products already
        // loaded in this transaction (e.g. fetched with the cart). Re-read them so stock checks
        // see what the previous lock holder committed, not the value from before we waited.
        locked.forEach(entityManager::refresh);
        return locked.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    /**
     * @param direction +1 to give stock back, -1 to take it.
     */
    private void adjustStock(Order order, Map<Long, Product> locked, int direction) {
        for (OrderItem item : order.getItems()) {
            Product product = locked.get(item.getProduct().getId());
            if (product == null) {
                continue;
            }
            int updated = product.getStockQty() + direction * item.getQuantity();
            product.setStockQty(Math.max(updated, 0));
        }
    }

    private void closeAsSold(Product product, UserEntity buyer) {
        product.setActive(false);
        product.setRemovalReason(ListingRemovalReason.SOLD_ON_LOCALYS);
        product.setRemovalNote(null);
        product.setRemovedAt(OffsetDateTime.now());
        productRepository.save(product);
        ProductRemovalEvent event = new ProductRemovalEvent();
        event.setProduct(product);
        event.setReason(ListingRemovalReason.SOLD_ON_LOCALYS);
        event.setActorUser(buyer);
        event.setOccurredAt(product.getRemovedAt());
        productRemovalEventRepository.save(event);
    }

    private OrderResponse toResponse(Order order, BigDecimal subtotal) {
        List<OrderResponse.OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderResponse.OrderItemResponse(
                        item.getProduct().getId(),
                        item.getProductNameSnapshot(),
                        item.getQuantity(),
                        item.getLineTotal()
                ))
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getStatus().name(),
                subtotal,
                order.getShippingPrice(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getCreatedAt(),
                items
        );
    }
}
