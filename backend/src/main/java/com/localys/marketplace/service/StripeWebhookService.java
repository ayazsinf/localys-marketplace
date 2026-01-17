package com.localys.marketplace.service;

import com.localys.marketplace.model.Order;
import com.localys.marketplace.model.Payment;
import com.localys.marketplace.model.enums.OrderStatus;
import com.localys.marketplace.model.enums.PaymentStatus;
import com.localys.marketplace.repository.OrderRepository;
import com.localys.marketplace.repository.PaymentRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class StripeWebhookService {

    private static final Logger logger = LoggerFactory.getLogger(StripeWebhookService.class);

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    @Value("${app.payment.stripe.webhook-secret:}")
    private String webhookSecret;

    public StripeWebhookService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public void handleEvent(String payload, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalArgumentException("Stripe webhook secret is missing");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException ex) {
            throw new IllegalArgumentException("Invalid Stripe signature");
        }

        switch (event.getType()) {
            case "checkout.session.completed" -> handleSessionCompleted(event);
            case "checkout.session.expired" -> handleSessionExpired(event);
            case "payment_intent.payment_failed" -> handlePaymentFailed(event);
            case "payment_intent.succeeded" -> handlePaymentSucceeded(event);
            default -> logger.debug("Ignoring Stripe event type {}", event.getType());
        }
    }

    private void handleSessionCompleted(Event event) {
        Session session = getStripeObject(event, Session.class);
        if (session == null) {
            return;
        }
        Optional<Payment> payment = paymentRepository.findByProviderSessionId(session.getId());
        if (payment.isEmpty() && session.getClientReferenceId() != null) {
            payment = parseOrderId(session.getClientReferenceId())
                    .flatMap(paymentRepository::findByOrder_Id);
        }
        updatePayment(payment, PaymentStatus.SUCCEEDED, OrderStatus.PAID);
    }

    private void handleSessionExpired(Event event) {
        Session session = getStripeObject(event, Session.class);
        if (session == null) {
            return;
        }
        Optional<Payment> payment = paymentRepository.findByProviderSessionId(session.getId());
        updatePayment(payment, PaymentStatus.CANCELLED, OrderStatus.CANCELLED);
    }

    private void handlePaymentFailed(Event event) {
        PaymentIntent intent = getStripeObject(event, PaymentIntent.class);
        if (intent == null) {
            return;
        }
        Optional<Payment> payment = paymentRepository.findByProviderPaymentIntentId(intent.getId());
        updatePayment(payment, PaymentStatus.FAILED, OrderStatus.PAYMENT_PENDING);
    }

    private void handlePaymentSucceeded(Event event) {
        PaymentIntent intent = getStripeObject(event, PaymentIntent.class);
        if (intent == null) {
            return;
        }
        Optional<Payment> payment = paymentRepository.findByProviderPaymentIntentId(intent.getId());
        updatePayment(payment, PaymentStatus.SUCCEEDED, OrderStatus.PAID);
    }

    private void updatePayment(Optional<Payment> payment, PaymentStatus status, OrderStatus orderStatus) {
        if (payment.isEmpty()) {
            logger.warn("Stripe webhook did not match a payment record.");
            return;
        }
        Payment entity = payment.get();
        entity.setStatus(status);
        paymentRepository.save(entity);

        Order order = entity.getOrder();
        if (order != null && orderStatus != null) {
            order.setStatus(orderStatus);
            orderRepository.save(order);
        }
    }

    private Optional<Long> parseOrderId(String referenceId) {
        try {
            return Optional.of(Long.parseLong(referenceId));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private <T> T getStripeObject(Event event, Class<T> clazz) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        Optional<? extends StripeObject> object = deserializer.getObject();
        if (object.isEmpty()) {
            logger.warn("Stripe webhook data object missing for event {}", event.getType());
            return null;
        }
        StripeObject value = object.get();
        if (clazz.isInstance(value)) {
            return clazz.cast(value);
        }
        logger.warn("Stripe webhook data type mismatch for event {}", event.getType());
        return null;
    }
}
