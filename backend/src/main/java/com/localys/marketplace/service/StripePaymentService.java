package com.localys.marketplace.service;

import com.localys.marketplace.dto.StripeCheckoutResponse;
import com.localys.marketplace.model.Order;
import com.localys.marketplace.model.OrderItem;
import com.localys.marketplace.model.Payment;
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
import java.util.List;
import java.util.UUID;

@Service
public class StripePaymentService {

    private static final Logger logger = LoggerFactory.getLogger(StripePaymentService.class);

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

        Payment payment = paymentRepository.findByOrder_Id(orderId)
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
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .setClientReferenceId(order.getId().toString());

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
