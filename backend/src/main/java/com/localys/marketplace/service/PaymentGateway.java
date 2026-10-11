package com.localys.marketplace.service;

import java.math.BigDecimal;

/**
 * Money movements after checkout. Kept behind an interface so the buyer-protection and payout
 * rules can be tested without calling Stripe.
 */
public interface PaymentGateway {

    /**
     * Gives {@code amount} of a captured payment back to the buyer.
     *
     * @param idempotencyKey the same key never refunds twice
     * @return the provider's refund id
     */
    String refund(String paymentIntentId, BigDecimal amount, String currency, String idempotencyKey);

    /** Opens a payout account for a seller; returns its id. */
    String createSellerAccount(String email, String country);

    /** A one-time link where the seller enters their identity and bank details. */
    String sellerOnboardingLink(String accountId, String refreshUrl, String returnUrl);

    /** Whether the seller finished onboarding and can receive money. */
    boolean payoutsEnabled(String accountId);

    /**
     * Sends {@code amount} to the seller's account, funded by the buyer's payment.
     *
     * @return the provider's transfer id
     */
    String transferToSeller(String accountId, BigDecimal amount, String currency, String paymentIntentId,
                            String transferGroup, String idempotencyKey);
}
