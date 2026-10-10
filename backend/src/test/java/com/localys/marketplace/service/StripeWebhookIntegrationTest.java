package com.localys.marketplace.service;

import com.localys.marketplace.dto.OrderCreateRequest;
import com.localys.marketplace.model.Address;
import com.localys.marketplace.model.Order;
import com.localys.marketplace.model.Payment;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.enums.OrderStatus;
import com.localys.marketplace.model.enums.PaymentStatus;
import com.localys.marketplace.repository.OrderRepository;
import com.localys.marketplace.repository.PaymentRepository;
import com.localys.marketplace.support.IntegrationTest;
import com.stripe.Stripe;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sends correctly signed Stripe events through the real webhook handler, covering the
 * at-least-once and out-of-order delivery Stripe allows.
 */
class StripeWebhookIntegrationTest extends IntegrationTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret";

    @Autowired
    private StripeWebhookService webhookService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;

    private Product product;
    private Long orderId;
    private String sessionId;

    @BeforeEach
    void setUp() throws Exception {
        UserEntity seller = data.user("seller");
        product = data.listing(data.vendor(seller), "40.00", 2);
        UserEntity buyer = data.user("buyer");
        Address address = data.address(buyer);
        data.addToCart(buyer, product, 1);
        orderId = orderService.createOrder(buyer.getId(), new OrderCreateRequest(
                List.of(product.getId()), address.getId(), "mondial", "card")).id();
        sessionId = "cs_test_" + UUID.randomUUID();
        data.payment(orderId, sessionId);
    }

    @Test
    void paidCheckoutMarksOrderPaidAndReplaysAreHarmless() throws Exception {
        String event = sessionEvent("checkout.session.completed", sessionId, "paid", "pi_" + UUID.randomUUID());

        send(event);
        send(event);

        assertThat(orderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(data.stockOf(product.getId())).isEqualTo(1);
    }

    @Test
    void expiryArrivingAfterPaymentDoesNotCancel() throws Exception {
        send(sessionEvent("checkout.session.completed", sessionId, "paid", "pi_" + UUID.randomUUID()));
        send(sessionEvent("checkout.session.expired", sessionId, "unpaid", null));

        assertThat(orderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void expiredActiveSessionCancelsOrderAndReleasesStock() throws Exception {
        send(sessionEvent("checkout.session.expired", sessionId, "unpaid", null));

        assertThat(orderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(data.stockOf(product.getId())).isEqualTo(2);
    }

    @Test
    void expiryOfAReplacedSessionIsIgnored() throws Exception {
        send(sessionEvent("checkout.session.expired", "cs_test_old_" + UUID.randomUUID(), "unpaid", null));

        assertThat(orderStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(data.stockOf(product.getId())).isEqualTo(1);
    }

    @Test
    void asyncPaymentKeepsReservationUntilItSucceeds() throws Exception {
        String intentId = "pi_" + UUID.randomUUID();
        send(sessionEvent("checkout.session.completed", sessionId, "unpaid", intentId));

        assertThat(orderStatus()).isEqualTo(OrderStatus.PAYMENT_CONFIRMING);
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(data.stockOf(product.getId())).isEqualTo(1);

        send(paymentIntentEvent("payment_intent.payment_failed", intentId));
        assertThat(orderStatus()).isEqualTo(OrderStatus.PAYMENT_CONFIRMING);

        send(sessionEvent("checkout.session.async_payment_succeeded", sessionId, "paid", intentId));
        assertThat(orderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void asyncPaymentFailureCancelsOrder() throws Exception {
        send(sessionEvent("checkout.session.completed", sessionId, "unpaid", "pi_" + UUID.randomUUID()));
        send(sessionEvent("checkout.session.async_payment_failed", sessionId, "unpaid", null));

        assertThat(orderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(data.stockOf(product.getId())).isEqualTo(2);
    }

    @Test
    void rejectsInvalidSignature() {
        String payload = sessionEvent("checkout.session.completed", sessionId, "paid", "pi_x");
        String header = "t=" + (System.currentTimeMillis() / 1000) + ",v1=deadbeef";

        assertThatThrownBy(() -> webhookService.handleEvent(payload, header))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid Stripe signature");
        assertThat(orderStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
    }

    private void send(String payload) throws Exception {
        long timestamp = System.currentTimeMillis() / 1000;
        String signature = Webhook.Util.computeHmacSha256(WEBHOOK_SECRET, timestamp + "." + payload);
        webhookService.handleEvent(payload, "t=" + timestamp + ",v1=" + signature);
    }

    private String sessionEvent(String type, String session, String paymentStatus, String paymentIntent) {
        String object = """
                {"id":"%s","object":"checkout.session","payment_status":"%s","payment_intent":%s,"client_reference_id":"%s"}"""
                .formatted(session, paymentStatus, paymentIntent == null ? "null" : "\"" + paymentIntent + "\"", orderId);
        return event(type, object);
    }

    private String paymentIntentEvent(String type, String intentId) {
        return event(type, """
                {"id":"%s","object":"payment_intent","status":"requires_payment_method"}""".formatted(intentId));
    }

    private String event(String type, String object) {
        return """
                {"id":"evt_%s","object":"event","api_version":"%s","type":"%s","data":{"object":%s}}"""
                .formatted(UUID.randomUUID(), Stripe.API_VERSION, type, object);
    }

    private OrderStatus orderStatus() {
        return orderRepository.findById(orderId).map(Order::getStatus).orElseThrow();
    }

    private Payment payment() {
        return paymentRepository.findByOrder_Id(orderId).orElseThrow();
    }
}
