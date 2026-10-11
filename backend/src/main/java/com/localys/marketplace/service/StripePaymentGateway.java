package com.localys.marketplace.service;

import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class StripePaymentGateway implements PaymentGateway {

    private static final Logger logger = LoggerFactory.getLogger(StripePaymentGateway.class);

    private final String secretKey;

    public StripePaymentGateway(@Value("${app.payment.stripe.secret-key:}") String secretKey) {
        this.secretKey = secretKey;
    }

    @Override
    public String refund(String paymentIntentId, BigDecimal amount, String currency, String idempotencyKey) {
        requireKey();
        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId)
                .setAmount(toStripeAmount(amount))
                .build();
        try {
            return Refund.create(params, options(idempotencyKey)).getId();
        } catch (StripeException ex) {
            logger.warn("Stripe refund failed for {}: {}", paymentIntentId, ex.getMessage());
            throw new IllegalStateException("Refund failed: " + ex.getMessage(), ex);
        }
    }

    private RequestOptions options(String idempotencyKey) {
        return RequestOptions.builder()
                .setApiKey(secretKey)
                .setIdempotencyKey(idempotencyKey)
                .build();
    }

    private void requireKey() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Stripe secret key is missing");
        }
    }

    static long toStripeAmount(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
