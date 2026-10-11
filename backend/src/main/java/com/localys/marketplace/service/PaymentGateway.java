package com.localys.marketplace.service;

import java.math.BigDecimal;

/**
 * Money movements after checkout. Kept behind an interface so the buyer-protection rules can
 * be tested without calling Stripe.
 */
public interface PaymentGateway {

    /**
     * Gives {@code amount} of a captured payment back to the buyer.
     *
     * @param idempotencyKey the same key never refunds twice
     * @return the provider's refund id
     */
    String refund(String paymentIntentId, BigDecimal amount, String currency, String idempotencyKey);
}
