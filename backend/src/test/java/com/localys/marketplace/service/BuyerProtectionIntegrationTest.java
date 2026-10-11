package com.localys.marketplace.service;

import com.localys.marketplace.dto.DisputeDto;
import com.localys.marketplace.dto.OrderCreateRequest;
import com.localys.marketplace.dto.OrderDto;
import com.localys.marketplace.model.Address;
import com.localys.marketplace.model.OrderItem;
import com.localys.marketplace.model.Payment;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.Vendor;
import com.localys.marketplace.model.enums.EscrowStatus;
import com.localys.marketplace.model.enums.NotificationType;
import com.localys.marketplace.model.enums.OrderStatus;
import com.localys.marketplace.model.enums.PaymentStatus;
import com.localys.marketplace.repository.NotificationRepository;
import com.localys.marketplace.repository.OrderItemRepository;
import com.localys.marketplace.repository.OrderRepository;
import com.localys.marketplace.repository.PaymentRepository;
import com.localys.marketplace.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuyerProtectionIntegrationTest extends IntegrationTest {

    @MockBean
    private PaymentGateway paymentGateway;

    @Autowired
    private BuyerProtectionService protectionService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserEntity seller;
    private Vendor vendor;
    private UserEntity buyer;
    private Address address;
    private String intentId;

    @BeforeEach
    void setUp() {
        seller = data.user("seller");
        vendor = data.vendor(seller);
        buyer = data.user("buyer");
        address = data.address(buyer);
        intentId = "pi_" + UUID.randomUUID();
        when(paymentGateway.refund(anyString(), any(), anyString(), anyString())).thenReturn("re_test");
    }

    @Test
    void confirmingReceiptReleasesThePayment() {
        Long orderId = paidOrder(listing("40.00"));
        OrderItem item = items(orderId).get(0);
        assertThat(item.getEscrowStatus()).isEqualTo(EscrowStatus.HELD);
        assertThat(orderRepository.findById(orderId).orElseThrow().getPaidAt()).isNotNull();

        orderService.shipItems(seller.getId(), orderId, "TRACK");
        OrderDto delivered = orderService.confirmDelivery(buyer.getId(), orderId);

        assertThat(delivered.items().get(0).escrowStatus()).isEqualTo("RELEASED");
        assertThat(notifications(seller, NotificationType.PAYMENT_RELEASED)).isEqualTo(1);
        assertThatThrownBy(() -> protectionService.openDispute(buyer.getId(), orderId, item.getId(), "DAMAGED", "Broken"))
                .hasMessage("Dispute not allowed for this item");
        verify(paymentGateway, never()).refund(anyString(), any(), anyString(), anyString());
    }

    @Test
    void sellerCanRefundADisputedItem() {
        Long orderId = paidOrder(listing("40.00"));
        Long itemId = items(orderId).get(0).getId();
        orderService.shipItems(seller.getId(), orderId, "TRACK");

        assertThatThrownBy(() -> protectionService.openDispute(buyer.getId(), orderId, itemId, "bogus", "x"))
                .hasMessage("Invalid dispute reason");
        assertThatThrownBy(() -> protectionService.openDispute(seller.getId(), orderId, itemId, "DAMAGED", "x"))
                .hasMessage("Order not found");
        DisputeDto dispute = protectionService.openDispute(buyer.getId(), orderId, itemId, "not_as_described", " Wrong colour ");
        assertThat(dispute.status()).isEqualTo("OPEN");
        assertThat(dispute.description()).isEqualTo("Wrong colour");
        assertThat(notifications(seller, NotificationType.DISPUTE_OPENED)).isEqualTo(1);
        // While disputed, confirming receipt cannot release the money.
        assertThatThrownBy(() -> orderService.confirmDelivery(buyer.getId(), orderId))
                .hasMessage("Order cannot be confirmed: nothing shipped yet");
        assertThatThrownBy(() -> protectionService.refundBySeller(buyer.getId(), dispute.id()))
                .hasMessage("Dispute not found");

        DisputeDto refunded = protectionService.refundBySeller(seller.getId(), dispute.id());

        assertThat(refunded.status()).isEqualTo("REFUNDED");
        // The only item: the shipping fee is refunded too.
        BigDecimal expected = new BigDecimal("40.00").add(orderRepository.findById(orderId).orElseThrow().getShippingPrice());
        verify(paymentGateway).refund(eq(intentId), argThat(amount -> amount.compareTo(expected) == 0), eq("EUR"),
                eq("refund-order-item-" + itemId));
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(paymentRepository.findByOrder_Id(orderId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(orderService.getForBuyer(buyer.getId(), orderId).items().get(0))
                .satisfies(dto -> {
                    assertThat(dto.escrowStatus()).isEqualTo("REFUNDED");
                    assertThat(dto.refundAmount()).isEqualByComparingTo(expected);
                    assertThat(dto.disputeStatus()).isEqualTo("REFUNDED");
                });
        assertThat(notifications(buyer, NotificationType.ORDER_REFUNDED)).isEqualTo(1);
        assertThatThrownBy(() -> protectionService.refundBySeller(seller.getId(), dispute.id()))
                .hasMessage("Dispute already closed");
    }

    @Test
    void contestedDisputesAreSettledByAnAdmin() {
        Long orderId = paidOrder(listing("40.00"));
        Long itemId = items(orderId).get(0).getId();
        DisputeDto dispute = protectionService.openDispute(buyer.getId(), orderId, itemId, "NOT_RECEIVED", "Nothing came");

        DisputeDto contested = protectionService.contest(seller.getId(), dispute.id(), "I sent it, see the tracking");
        assertThat(contested.status()).isEqualTo("CONTESTED");
        assertThat(protectionService.listForAdmin("ACTIVE")).extracting(DisputeDto::id).contains(dispute.id());
        assertThatThrownBy(() -> protectionService.resolve(data.user("admin").getId(), dispute.id(), "maybe", null))
                .hasMessage("Invalid dispute outcome");

        DisputeDto resolved = protectionService.resolve(data.user("admin").getId(), dispute.id(), "release", "Tracking shows delivery");

        assertThat(resolved.status()).isEqualTo("RELEASED");
        assertThat(resolved.resolutionNote()).isEqualTo("Tracking shows delivery");
        assertThat(items(orderId).get(0).getEscrowStatus()).isEqualTo(EscrowStatus.RELEASED);
        assertThat(notifications(buyer, NotificationType.DISPUTE_CLOSED)).isEqualTo(1);
        assertThat(notifications(seller, NotificationType.DISPUTE_CLOSED)).isEqualTo(1);
        verify(paymentGateway, never()).refund(anyString(), any(), anyString(), anyString());
    }

    @Test
    void withdrawingPutsTheMoneyBackOnHold() {
        Long orderId = paidOrder(listing("40.00"));
        Long itemId = items(orderId).get(0).getId();
        DisputeDto dispute = protectionService.openDispute(buyer.getId(), orderId, itemId, "OTHER", "Changed my mind");

        assertThat(protectionService.withdraw(buyer.getId(), dispute.id()).status()).isEqualTo("WITHDRAWN");
        assertThat(items(orderId).get(0).getEscrowStatus()).isEqualTo(EscrowStatus.HELD);
        // A new dispute can be opened afterwards.
        assertThat(protectionService.openDispute(buyer.getId(), orderId, itemId, "DAMAGED", "Scratched").status())
                .isEqualTo("OPEN");
    }

    @Test
    void refundingOneOfSeveralItemsKeepsTheShippingFeeAndTheOrder() {
        Product first = listing("10.00");
        Product second = listing("30.00");
        Long orderId = paidOrder(first, second);
        OrderItem firstItem = items(orderId).stream().filter(item -> item.getProduct().getId().equals(first.getId())).findFirst().orElseThrow();

        DisputeDto dispute = protectionService.openDispute(buyer.getId(), orderId, firstItem.getId(), "NOT_RECEIVED", "Missing");
        protectionService.refundBySeller(seller.getId(), dispute.id());

        verify(paymentGateway).refund(eq(intentId), argThat(amount -> amount.compareTo(new BigDecimal("10.00")) == 0),
                anyString(), anyString());
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);

        // Shipping the remaining item completes the order; the refunded one is not expected.
        assertThat(orderService.shipItems(seller.getId(), orderId, "TRACK").status()).isEqualTo("SHIPPED");
        assertThat(orderService.confirmDelivery(buyer.getId(), orderId).status()).isEqualTo("DELIVERED");
    }

    @Test
    void deadlinesRefundUnshippedItemsAndReleaseUnconfirmedOnes() {
        Long unshippedOrder = paidOrder(listing("20.00"));
        Long shippedOrder = paidOrder(listing("25.00"));
        orderService.shipItems(seller.getId(), shippedOrder, "TRACK");
        jdbcTemplate.update("update orders set paid_at = ? where id = ?", OffsetDateTime.now().minusDays(8), unshippedOrder);
        jdbcTemplate.update("update order_items set shipped_at = ? where order_id = ?", OffsetDateTime.now().minusDays(15), shippedOrder);

        protectionService.processDeadlines();

        assertThat(items(unshippedOrder).get(0).getEscrowStatus()).isEqualTo(EscrowStatus.REFUNDED);
        assertThat(orderRepository.findById(unshippedOrder).orElseThrow().getStatus()).isEqualTo(OrderStatus.REFUNDED);
        OrderItem released = items(shippedOrder).get(0);
        assertThat(released.getEscrowStatus()).isEqualTo(EscrowStatus.RELEASED);
        assertThat(released.getDeliveredAt()).isNotNull();
        assertThat(orderRepository.findById(shippedOrder).orElseThrow().getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    private Product listing(String price) {
        return data.listing(vendor, price, 1);
    }

    private Long paidOrder(Product... products) {
        for (Product product : products) {
            data.addToCart(buyer, product, 1);
        }
        Long orderId = orderService.createOrder(buyer.getId(), new OrderCreateRequest(
                java.util.Arrays.stream(products).map(Product::getId).toList(), address.getId(), "mondial", "card")).id();
        Payment payment = data.payment(orderId, "cs_" + UUID.randomUUID());
        payment.setProviderPaymentIntentId(intentId);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        paymentRepository.save(payment);
        orderService.markPaid(orderId);
        return orderId;
    }

    private List<OrderItem> items(Long orderId) {
        return orderItemRepository.findAll().stream()
                .filter(item -> item.getOrder().getId().equals(orderId))
                .toList();
    }

    private long notifications(UserEntity user, NotificationType type) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(notification -> notification.getType() == type)
                .count();
    }
}
