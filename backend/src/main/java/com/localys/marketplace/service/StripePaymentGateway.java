package com.localys.marketplace.service;

import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.Transfer;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.TransferCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

/**
 * Stripe Connect with "separate charges and transfers": buyers pay the platform, and each
 * seller's share is transferred to their Express account once buyer protection releases it.
 */
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

    @Override
    public CheckoutSession createCheckout(String itemName, BigDecimal amount, String currency, String successUrl,
                                          String cancelUrl, Map<String, String> metadata, String idempotencyKey) {
        requireKey();
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .putAllMetadata(metadata)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(currency.toLowerCase(Locale.ROOT))
                                .setUnitAmount(toStripeAmount(amount))
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(itemName)
                                        .build())
                                .build())
                        .build())
                .build();
        try {
            Session session = Session.create(params, options(idempotencyKey));
            return new CheckoutSession(session.getId(), session.getUrl());
        } catch (StripeException ex) {
            logger.warn("Stripe checkout failed for {}: {}", itemName, ex.getMessage());
            throw new IllegalStateException("Stripe checkout failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public String createSellerAccount(String email, String country) {
        requireKey();
        AccountCreateParams params = AccountCreateParams.builder()
                .setType(AccountCreateParams.Type.EXPRESS)
                .setCountry(country.toUpperCase(Locale.ROOT))
                .setEmail(email)
                .setCapabilities(AccountCreateParams.Capabilities.builder()
                        .setTransfers(AccountCreateParams.Capabilities.Transfers.builder().setRequested(true).build())
                        .build())
                .build();
        try {
            return Account.create(params, options(null)).getId();
        } catch (StripeException ex) {
            logger.warn("Stripe account creation failed: {}", ex.getMessage());
            throw new IllegalStateException("Payout account could not be created: " + ex.getMessage(), ex);
        }
    }

    @Override
    public String sellerOnboardingLink(String accountId, String refreshUrl, String returnUrl) {
        requireKey();
        AccountLinkCreateParams params = AccountLinkCreateParams.builder()
                .setAccount(accountId)
                .setRefreshUrl(refreshUrl)
                .setReturnUrl(returnUrl)
                .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                .build();
        try {
            return AccountLink.create(params, options(null)).getUrl();
        } catch (StripeException ex) {
            logger.warn("Stripe onboarding link failed for {}: {}", accountId, ex.getMessage());
            throw new IllegalStateException("Payout onboarding is unavailable: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean payoutsEnabled(String accountId) {
        requireKey();
        try {
            Account account = Account.retrieve(accountId, options(null));
            return Boolean.TRUE.equals(account.getPayoutsEnabled());
        } catch (StripeException ex) {
            logger.warn("Could not read Stripe account {}: {}", accountId, ex.getMessage());
            return false;
        }
    }

    @Override
    public String transferToSeller(String accountId, BigDecimal amount, String currency, String paymentIntentId,
                                   String transferGroup, String idempotencyKey) {
        requireKey();
        try {
            TransferCreateParams.Builder params = TransferCreateParams.builder()
                    .setAmount(toStripeAmount(amount))
                    .setCurrency(currency.toLowerCase(Locale.ROOT))
                    .setDestination(accountId)
                    .setTransferGroup(transferGroup);
            // Funding the transfer from the buyer's charge works even before that money is
            // available in the platform balance.
            if (paymentIntentId != null) {
                String chargeId = PaymentIntent.retrieve(paymentIntentId, options(null)).getLatestCharge();
                if (chargeId != null) {
                    params.setSourceTransaction(chargeId);
                }
            }
            return Transfer.create(params.build(), options(idempotencyKey)).getId();
        } catch (StripeException ex) {
            logger.warn("Stripe transfer to {} failed: {}", accountId, ex.getMessage());
            throw new IllegalStateException("Payout failed: " + ex.getMessage(), ex);
        }
    }

    private RequestOptions options(String idempotencyKey) {
        RequestOptions.RequestOptionsBuilder builder = RequestOptions.builder().setApiKey(secretKey);
        if (idempotencyKey != null) {
            builder.setIdempotencyKey(idempotencyKey);
        }
        return builder.build();
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
