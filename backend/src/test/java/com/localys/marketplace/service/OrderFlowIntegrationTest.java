package com.localys.marketplace.service;

import com.localys.marketplace.dto.OrderCreateRequest;
import com.localys.marketplace.dto.OrderDto;
import com.localys.marketplace.dto.OrderResponse;
import com.localys.marketplace.model.Address;
import com.localys.marketplace.model.Order;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.Vendor;
import com.localys.marketplace.model.enums.CartStatus;
import com.localys.marketplace.model.enums.ListingRemovalReason;
import com.localys.marketplace.model.enums.NotificationType;
import com.localys.marketplace.model.enums.OrderStatus;
import com.localys.marketplace.repository.CartRepository;
import com.localys.marketplace.repository.NotificationRepository;
import com.localys.marketplace.repository.OrderRepository;
import com.localys.marketplace.repository.ProductRepository;
import com.localys.marketplace.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderFlowIntegrationTest extends IntegrationTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderExpirationService orderExpirationService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CartRepository cartRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    private UserEntity seller;
    private Vendor vendor;
    private UserEntity buyer;
    private Address buyerAddress;

    @BeforeEach
    void setUp() {
        seller = data.user("seller");
        vendor = data.vendor(seller);
        buyer = data.user("buyer");
        buyerAddress = data.address(buyer);
    }

    @Test
    void createOrderReservesStockAndEmptiesCart() {
        Product product = data.listing(vendor, "20.00", 3);
        data.addToCart(buyer, product, 2);

        OrderResponse order = order(buyer, buyerAddress, product);

        assertThat(order.status()).isEqualTo("PAYMENT_PENDING");
        assertThat(order.subtotal()).isEqualByComparingTo("40.00");
        // Shipping is priced on the server, whatever the client sends.
        assertThat(order.shippingPrice()).isEqualByComparingTo("4.90");
        assertThat(order.total()).isEqualByComparingTo("44.90");
        assertThat(data.stockOf(product.getId())).isEqualTo(1);
        assertThat(cartItemCount(buyer)).isZero();
    }

    @Test
    void createOrderRejectsMoreThanStock() {
        Product product = data.listing(vendor, "10.00", 1);
        data.addToCart(buyer, product, 2);

        assertThatThrownBy(() -> order(buyer, buyerAddress, product))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product out of stock");
        assertThat(data.stockOf(product.getId())).isEqualTo(1);
    }

    @Test
    void sellerCannotBuyOwnListing() {
        Product product = data.listing(vendor, "10.00", 1);
        Address sellerAddress = data.address(seller);
        data.addToCart(seller, product, 1);

        assertThatThrownBy(() -> order(seller, sellerAddress, product))
                .hasMessage("Cannot buy own product");
    }

    @Test
    void lastItemCanOnlyBeReservedOnce() throws Exception {
        Product product = data.listing(vendor, "15.00", 1);
        UserEntity otherBuyer = data.user("other-buyer");
        Address otherAddress = data.address(otherBuyer);
        data.addToCart(buyer, product, 1);
        data.addToCart(otherBuyer, product, 1);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<OrderResponse>> attempts = List.of(
                    () -> { start.await(); return order(buyer, buyerAddress, product); },
                    () -> { start.await(); return order(otherBuyer, otherAddress, product); }
            );
            List<Future<OrderResponse>> futures = new ArrayList<>();
            attempts.forEach(attempt -> futures.add(pool.submit(attempt)));
            start.countDown();

            int succeeded = 0;
            int outOfStock = 0;
            for (Future<OrderResponse> future : futures) {
                try {
                    future.get();
                    succeeded++;
                } catch (java.util.concurrent.ExecutionException ex) {
                    assertThat(ex.getCause()).hasMessage("Product out of stock");
                    outOfStock++;
                }
            }
            assertThat(succeeded).isEqualTo(1);
            assertThat(outOfStock).isEqualTo(1);
            assertThat(data.stockOf(product.getId())).isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void cancellingUnpaidOrderReleasesStockOnceAndRestoresCart() {
        Product product = data.listing(vendor, "12.00", 2);
        data.addToCart(buyer, product, 2);
        Long orderId = order(buyer, buyerAddress, product).id();
        assertThat(data.stockOf(product.getId())).isZero();

        orderService.cancelUnpaid(orderId);
        orderService.cancelUnpaid(orderId);

        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(data.stockOf(product.getId())).isEqualTo(2);
        assertThat(cartItemCount(buyer)).isEqualTo(1);
    }

    @Test
    void paidOrderIsNeverCancelled() {
        Product product = data.listing(vendor, "12.00", 2);
        data.addToCart(buyer, product, 1);
        Long orderId = order(buyer, buyerAddress, product).id();

        orderService.markPaid(orderId);
        orderService.cancelUnpaid(orderId);

        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PAID);
        assertThat(data.stockOf(product.getId())).isEqualTo(1);
    }

    @Test
    void markPaidClosesSoldOutListingAndIsIdempotent() {
        Product product = data.listing(vendor, "30.00", 1);
        data.addToCart(buyer, product, 1);
        Long orderId = order(buyer, buyerAddress, product).id();

        orderService.markPaid(orderId);
        orderService.markPaid(orderId);

        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PAID);
        Product closed = productRepository.findById(product.getId()).orElseThrow();
        assertThat(closed.isActive()).isFalse();
        assertThat(closed.getRemovalReason()).isEqualTo(ListingRemovalReason.SOLD_ON_LOCALYS);
        assertThat(closed.getStockQty()).isZero();
        assertThat(notificationCount(buyer, NotificationType.ORDER_PAID)).isEqualTo(1);
        assertThat(notificationCount(seller, NotificationType.ORDER_SOLD)).isEqualTo(1);
    }

    @Test
    void lateSuccessfulPaymentReReservesStockOfCancelledOrder() {
        Product product = data.listing(vendor, "30.00", 3);
        data.addToCart(buyer, product, 1);
        Long orderId = order(buyer, buyerAddress, product).id();
        orderService.cancelUnpaid(orderId);
        assertThat(data.stockOf(product.getId())).isEqualTo(3);

        orderService.markPaid(orderId);

        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PAID);
        assertThat(data.stockOf(product.getId())).isEqualTo(2);
    }

    @Test
    void shippingAndDeliveryFlow() {
        Product product = data.listing(vendor, "25.00", 1);
        data.addToCart(buyer, product, 1);
        Long orderId = order(buyer, buyerAddress, product).id();

        assertThatThrownBy(() -> orderService.shipItems(seller.getId(), orderId, "TRACK-1"))
                .hasMessage("Order cannot be shipped in its current state");
        assertThatThrownBy(() -> orderService.confirmDelivery(buyer.getId(), orderId))
                .hasMessage("Order cannot be confirmed: nothing shipped yet");

        orderService.markPaid(orderId);

        // Unpaid orders are hidden from the seller; paid ones become visible.
        assertThat(orderService.listSales(seller.getId()))
                .extracting(OrderDto::id).contains(orderId);
        UserEntity stranger = data.user("stranger");
        assertThatThrownBy(() -> orderService.shipItems(stranger.getId(), orderId, "TRACK-1"))
                .hasMessage("Order not found");
        assertThatThrownBy(() -> orderService.shipItems(seller.getId(), orderId, "  "))
                .hasMessage("Tracking number is required");

        OrderDto shipped = orderService.shipItems(seller.getId(), orderId, " TRACK-1 ");
        assertThat(shipped.status()).isEqualTo("SHIPPED");
        assertThat(shipped.items().get(0).trackingNumber()).isEqualTo("TRACK-1");
        assertThat(notificationCount(buyer, NotificationType.ORDER_SHIPPED)).isEqualTo(1);
        assertThatThrownBy(() -> orderService.shipItems(seller.getId(), orderId, "TRACK-2"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> orderService.confirmDelivery(stranger.getId(), orderId))
                .hasMessage("Order not found");
        OrderDto delivered = orderService.confirmDelivery(buyer.getId(), orderId);
        assertThat(delivered.status()).isEqualTo("DELIVERED");
        assertThat(notificationCount(seller, NotificationType.ORDER_DELIVERED)).isEqualTo(1);
    }

    @Test
    void sellersShipTheirOwnItemsIndependently() {
        UserEntity secondSeller = data.user("second-seller");
        Vendor secondVendor = data.vendor(secondSeller);
        Product first = data.listing(vendor, "10.00", 1);
        Product second = data.listing(secondVendor, "15.00", 1);
        data.addToCart(buyer, first, 1);
        data.addToCart(buyer, second, 1);
        Long orderId = orderService.createOrder(buyer.getId(), new OrderCreateRequest(
                List.of(first.getId(), second.getId()), buyerAddress.getId(), "colissimo", "card")).id();
        orderService.markPaid(orderId);

        OrderDto firstView = orderService.shipItems(seller.getId(), orderId, "T-A");
        // Each seller only sees their own items and, on a split order, no shipping fee.
        assertThat(firstView.items()).hasSize(1);
        assertThat(firstView.shippingPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PAID);

        orderService.confirmDelivery(buyer.getId(), orderId);
        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PAID);

        orderService.shipItems(secondSeller.getId(), orderId, "T-B");
        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.SHIPPED);
        orderService.confirmDelivery(buyer.getId(), orderId);
        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void expirationJobCancelsOnlyAbandonedOrders() {
        Product product = data.listing(vendor, "10.00", 5);
        data.addToCart(buyer, product, 2);
        Long abandoned = order(buyer, buyerAddress, product).id();
        data.backdateOrder(abandoned, OffsetDateTime.now().minusHours(2));

        UserEntity freshBuyer = data.user("fresh-buyer");
        Address freshAddress = data.address(freshBuyer);
        data.addToCart(freshBuyer, product, 1);
        Long fresh = order(freshBuyer, freshAddress, product).id();
        assertThat(data.stockOf(product.getId())).isEqualTo(2);

        orderExpirationService.cancelAbandonedOrders();

        assertThat(statusOf(abandoned)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(statusOf(fresh)).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(data.stockOf(product.getId())).isEqualTo(4);
    }

    private OrderResponse order(UserEntity user, Address address, Product product) {
        return orderService.createOrder(user.getId(), new OrderCreateRequest(
                List.of(product.getId()), address.getId(), "mondial", "card"));
    }

    private OrderStatus statusOf(Long orderId) {
        return orderRepository.findById(orderId).map(Order::getStatus).orElseThrow();
    }

    private int cartItemCount(UserEntity user) {
        return inTx(() -> cartRepository.findByUserIdAndStatus(user.getId(), CartStatus.ACTIVE)
                .map(cart -> cart.getItems().size())
                .orElse(0));
    }

    private long notificationCount(UserEntity user, NotificationType type) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(notification -> notification.getType() == type)
                .count();
    }
}
