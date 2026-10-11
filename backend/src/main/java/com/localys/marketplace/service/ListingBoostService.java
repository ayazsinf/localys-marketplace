package com.localys.marketplace.service;

import com.localys.marketplace.model.ListingBoost;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.enums.ModerationStatus;
import com.localys.marketplace.repository.ListingBoostRepository;
import com.localys.marketplace.repository.ProductRepository;
import com.localys.marketplace.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Paid "featured" listings: the seller pays through Stripe Checkout and the listing is shown
 * first in newest-first results for {@code boost-days}. Buying again extends the period.
 */
@Service
public class ListingBoostService {

    public record BoostOffer(BigDecimal price, String currency, int days) {
    }

    private final ListingBoostRepository boostRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PaymentGateway paymentGateway;
    private final BoostOffer offer;
    private final String publicUrl;

    public ListingBoostService(
            ListingBoostRepository boostRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            PaymentGateway paymentGateway,
            @Value("${app.marketplace.boost-price:4.99}") BigDecimal price,
            @Value("${app.marketplace.boost-currency:EUR}") String currency,
            @Value("${app.marketplace.boost-days:7}") int days,
            @Value("${app.public-url:http://localhost:4200}") String publicUrl
    ) {
        this.boostRepository = boostRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.paymentGateway = paymentGateway;
        this.offer = new BoostOffer(price, currency, days);
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    public BoostOffer offer() {
        return offer;
    }

    /** Starts the payment for featuring the seller's own online listing; returns the checkout URL. */
    @Transactional
    public String startCheckout(Long sellerUserId, Long productId) {
        Product product = productRepository.findById(productId)
                .filter(found -> found.getVendor() != null && found.getVendor().getUser() != null
                        && found.getVendor().getUser().getId().equals(sellerUserId))
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        OffsetDateTime now = OffsetDateTime.now();
        if (!product.isActive() || product.getRemovedAt() != null
                || product.getModerationStatus() != ModerationStatus.APPROVED
                || product.getExpiresAt() == null || !product.getExpiresAt().isAfter(now)) {
            throw new IllegalArgumentException("Only online listings can be boosted");
        }
        UserEntity seller = userRepository.findById(sellerUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        ListingBoost boost = new ListingBoost();
        boost.setProduct(product);
        boost.setUser(seller);
        boost.setDays(offer.days());
        boost.setAmount(offer.price());
        boost.setCurrency(offer.currency());
        boost.setStatus(ListingBoost.Status.PENDING);
        boostRepository.save(boost);

        PaymentGateway.CheckoutSession session = paymentGateway.createCheckout(
                "Featured listing: " + product.getName(),
                offer.price(),
                offer.currency(),
                publicUrl + "/listings?boost=success",
                publicUrl + "/listings?boost=canceled",
                Map.of("kind", "BOOST", "boostId", String.valueOf(boost.getId())),
                "boost-" + boost.getId());
        boost.setProviderSessionId(session.id());
        boostRepository.save(boost);
        return session.url();
    }

    /**
     * Applies a paid boost. Returns false when the session is not a boost (so the caller treats
     * it as an order payment). Idempotent: Stripe can deliver the event more than once.
     */
    @Transactional
    public boolean onSessionPaid(String sessionId) {
        ListingBoost boost = boostRepository.findBySessionForUpdate(sessionId).orElse(null);
        if (boost == null) {
            return false;
        }
        if (boost.getStatus() == ListingBoost.Status.PENDING) {
            OffsetDateTime now = OffsetDateTime.now();
            Product product = boost.getProduct();
            OffsetDateTime start = product.getBoostedUntil() != null && product.getBoostedUntil().isAfter(now)
                    ? product.getBoostedUntil()
                    : now;
            product.setBoostedUntil(start.plus(Duration.ofDays(boost.getDays())));
            productRepository.save(product);
            boost.setStatus(ListingBoost.Status.PAID);
            boost.setPaidAt(now);
            boostRepository.save(boost);
        }
        return true;
    }

    /** The checkout expired or failed. Returns false when the session is not a boost. */
    @Transactional
    public boolean onSessionClosedUnpaid(String sessionId) {
        ListingBoost boost = boostRepository.findBySessionForUpdate(sessionId).orElse(null);
        if (boost == null) {
            return false;
        }
        if (boost.getStatus() == ListingBoost.Status.PENDING) {
            boost.setStatus(ListingBoost.Status.CANCELLED);
            boostRepository.save(boost);
        }
        return true;
    }
}
