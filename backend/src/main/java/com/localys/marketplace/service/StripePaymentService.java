package com.localys.marketplace.service;

import com.localys.marketplace.dto.StripeCheckoutResponse;
import com.localys.marketplace.model.Order;
import com.localys.marketplace.model.OrderItem;
import com.localys.marketplace.model.Payment;
import com.localys.marketplace.model.enums.OrderStatus;
import com.localys.marketplace.model.enums.PaymentProvider;
import com.localys.marketplace.model.enums.PaymentStatus;
import com.localys.marketplace.repository.OrderRepository;
import com.localys.marketplace.repository.PaymentRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class StripePaymentService {

    private static final Logger logger = LoggerFactory.getLogger(StripePaymentService.class);

    /** Must stay below {@link OrderExpirationService}'s cleanup threshold. */
    static final Duration SESSION_TTL = Duration.ofMinutes(31);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    @Value("${app.payment.stripe.secret-key:}")
    private String stripeSecretKey;

    @Value("${app.payment.stripe.success-url:http://localhost:4200/checkout?success=true}")
    private String successUrl;

    @Value("${app.payment.stripe.cancel-url:http://localhost:4200/checkout?canceled=true}")
    private String cancelUrl;

    public StripePaymentService(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository
    ) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
    }

    @PostConstruct
    public void init() {
        if (stripeSecretKey != null && !stripeSecretKey.isBlank()) {
            Stripe.apiKey = stripeSecretKey;
        }
    }

    public StripeCheckoutResponse createCheckoutSession(Long userId, Long orderId) {
        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            throw new IllegalArgumentException("Stripe secret key is missing");
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if (!order.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Order not found");
        }
        if (order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            throw new IllegalArgumentException("Order is not awaiting payment");
        }

        Optional<Payment> existingPayment = paymentRepository.findByOrder_Id(orderId);
        Optional<String> openSessionUrl = existingPayment.flatMap(this::findOpenSessionUrl);
        if (openSessionUrl.isPresent()) {
            // Never open a second session for the same order: the customer could pay twice.
            return new StripeCheckoutResponse(openSessionUrl.get());
        }
        OffsetDateTime lastSessionStart = order.getCreatedAt()
                .plus(OrderExpirationService.UNPAID_ORDER_TTL)
                .minus(SESSION_TTL);
        if (OffsetDateTime.now().isAfter(lastSessionStart)) {
            // A new session would outlive the order's reservation (see OrderExpirationService).
            throw new IllegalArgumentException("Order is no longer payable");
        }

        Payment payment = existingPayment
                .orElseGet(() -> {
                    Payment newPayment = new Payment();
                    newPayment.setOrder(order);
                    newPayment.setProvider(PaymentProvider.STRIPE);
                    newPayment.setStatus(PaymentStatus.REQUIRES_PAYMENT);
                    newPayment.setAmount(order.getTotalAmount());
                    newPayment.setCurrency(order.getCurrency());
                    newPayment.setIdempotencyKey(UUID.randomUUID().toString());
                    return newPayment;
                });

        SessionCreateParams.Builder sessionBuilder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(withOrderId(successUrl, order.getId()))
                .setCancelUrl(withOrderId(cancelUrl, order.getId()))
                .setClientReferenceId(order.getId().toString())
                // Bounded lifetime so the stock reservation is released quickly when the
                // customer abandons the payment (Stripe minimum is 30 minutes).
                .setExpiresAt(Instant.now().plus(SESSION_TTL).getEpochSecond());

        List<SessionCreateParams.LineItem> lineItems = order.getItems().stream()
                .map(this::toLineItem)
                .toList();
        sessionBuilder.addAllLineItem(lineItems);

        if (order.getShippingPrice() != null && order.getShippingPrice().compareTo(BigDecimal.ZERO) > 0) {
            sessionBuilder.addLineItem(toShippingLine(order.getShippingPrice(), order.getCurrency()));
        }

        try {
            Session session = Session.create(sessionBuilder.build());
            payment.setProviderSessionId(session.getId());
            payment.setProviderPaymentIntentId(session.getPaymentIntent());
            payment.setStatus(PaymentStatus.PROCESSING);
            paymentRepository.save(payment);
            return new StripeCheckoutResponse(session.getUrl());
        } catch (StripeException ex) {
            logger.warn("Stripe checkout failed for order {}: {}", orderId, ex.getMessage());
            throw new IllegalArgumentException("Stripe error: " + ex.getMessage());
        }
    }

    /** Lets the frontend land on the right order after Stripe redirects back. */
    private String withOrderId(String url, Long orderId) {
        return url + (url.contains("?") ? "&" : "?") + "orderId=" + orderId;
    }

    private Optional<String> findOpenSessionUrl(Payment payment) {
        if (payment.getProviderSessionId() == null) {
            return Optional.empty();
        }
        try {
            Session session = Session.retrieve(payment.getProviderSessionId());
            return "open".equals(session.getStatus()) ? Optional.ofNullable(session.getUrl()) : Optional.empty();
        } catch (StripeException ex) {
            logger.warn("Could not retrieve Stripe session {}: {}", payment.getProviderSessionId(), ex.getMessage());
            return Optional.empty();
        }
    }

    private SessionCreateParams.LineItem toLineItem(OrderItem item) {
        long unitAmount = toStripeAmount(item.getUnitPriceSnapshot());
        return SessionCreateParams.LineItem.builder()
                .setQuantity((long) item.getQuantity())
                .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency(item.getCurrency().toLowerCase())
                        .setUnitAmount(unitAmount)
                        .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                .setName(item.getProductNameSnapshot())
                                .build())
                        .build())
                .build();
    }

    private SessionCreateParams.LineItem toShippingLine(BigDecimal shipping, String currency) {
        return SessionCreateParams.LineItem.builder()
                .setQuantity(1L)
                .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency(currency.toLowerCase())
                        .setUnitAmount(toStripeAmount(shipping))
                        .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                .setName("Shipping")
                                .build())
                        .build())
                .build();
    }

    private long toStripeAmount(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

}
