package com.localys.marketplace.service;

import com.localys.marketplace.model.Payment;
import com.localys.marketplace.model.enums.PaymentStatus;
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

/**
 * Translates Stripe events into order state changes. Stripe delivers events at least once
 * and in no guaranteed order, so every transition is delegated to {@link OrderService},
 * which only ever moves an order forward (a paid order is never cancelled by a late event).
 */
@Service
public class StripeWebhookService {

    private static final Logger logger = LoggerFactory.getLogger(StripeWebhookService.class);

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;

    @Value("${app.payment.stripe.webhook-secret:}")
    private String webhookSecret;

    public StripeWebhookService(
            PaymentRepository paymentRepository,
            OrderService orderService
    ) {
        this.paymentRepository = paymentRepository;
        this.orderService = orderService;
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
            case "checkout.session.async_payment_succeeded" -> handleAsyncPaymentSucceeded(event);
            case "checkout.session.async_payment_failed" -> handleSessionClosedUnpaid(event, PaymentStatus.FAILED);
            case "checkout.session.expired" -> handleSessionClosedUnpaid(event, PaymentStatus.CANCELLED);
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
        Optional<Payment> match = findBySession(session);
        if (match.isEmpty()) {
            logger.warn("Stripe session {} did not match a payment record.", session.getId());
            return;
        }
        Payment payment = match.get();
        if (session.getPaymentIntent() != null) {
            payment.setProviderPaymentIntentId(session.getPaymentIntent());
        }
        // "completed" only means the customer finished the checkout form. For async methods
        // the money has not arrived yet (payment_status = "unpaid").
        String paymentStatus = session.getPaymentStatus();
        if ("paid".equals(paymentStatus) || "no_payment_required".equals(paymentStatus)) {
            markSucceeded(payment);
        } else {
            if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
                payment.setStatus(PaymentStatus.PROCESSING);
            }
            paymentRepository.save(payment);
            orderService.markPaymentConfirming(payment.getOrder().getId());
        }
    }

    private void handleAsyncPaymentSucceeded(Event event) {
        Session session = getStripeObject(event, Session.class);
        if (session == null) {
            return;
        }
        findBySession(session).ifPresentOrElse(
                this::markSucceeded,
                () -> logger.warn("Stripe session {} did not match a payment record.", session.getId())
        );
    }

    private void handleSessionClosedUnpaid(Event event, PaymentStatus paymentStatus) {
        Session session = getStripeObject(event, Session.class);
        if (session == null) {
            return;
        }
        // Only the session currently attached to the payment may cancel the order; an old
        // session expiring must not cancel an order the customer is paying with a newer one.
        Optional<Payment> match = paymentRepository.findByProviderSessionId(session.getId());
        if (match.isEmpty()) {
            logger.debug("Stripe session {} is not the active session of any payment.", session.getId());
            return;
        }
        Payment payment = match.get();
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return;
        }
        payment.setStatus(paymentStatus);
        paymentRepository.save(payment);
        orderService.cancelUnpaid(payment.getOrder().getId());
    }

    private void handlePaymentFailed(Event event) {
        PaymentIntent intent = getStripeObject(event, PaymentIntent.class);
        if (intent == null) {
            return;
        }
        // A failed attempt inside Checkout is retryable until the session expires, so the
        // order (and its stock reservation) stays open; expiry releases it.
        paymentRepository.findByProviderPaymentIntentId(intent.getId())
                .filter(payment -> payment.getStatus() != PaymentStatus.SUCCEEDED)
                .ifPresent(payment -> {
                    payment.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(payment);
                });
    }

    private void handlePaymentSucceeded(Event event) {
        PaymentIntent intent = getStripeObject(event, PaymentIntent.class);
        if (intent == null) {
            return;
        }
        // The intent id is only known after checkout.session.completed, which handles the
        // common case; this covers the event arriving later or being replayed.
        paymentRepository.findByProviderPaymentIntentId(intent.getId())
                .ifPresent(this::markSucceeded);
    }

    private void markSucceeded(Payment payment) {
        payment.setStatus(PaymentStatus.SUCCEEDED);
        paymentRepository.save(payment);
        orderService.markPaid(payment.getOrder().getId());
    }

    private Optional<Payment> findBySession(Session session) {
        Optional<Payment> payment = paymentRepository.findByProviderSessionId(session.getId());
        if (payment.isEmpty() && session.getClientReferenceId() != null) {
            payment = parseOrderId(session.getClientReferenceId())
                    .flatMap(paymentRepository::findByOrder_Id);
        }
        return payment;
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
