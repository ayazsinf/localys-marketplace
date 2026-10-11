package com.localys.marketplace.service;

import com.localys.marketplace.dto.CartDto;
import com.localys.marketplace.dto.OfferDto;
import com.localys.marketplace.model.Offer;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.ProductImage;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.enums.ModerationStatus;
import com.localys.marketplace.model.enums.OfferStatus;
import com.localys.marketplace.repository.OfferRepository;
import com.localys.marketplace.repository.ProductRepository;
import com.localys.marketplace.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Price negotiation on a listing. The buyer offers less than the asking price; the seller
 * accepts, declines or counters; the buyer answers a counter. An accepted offer can be bought
 * at its price for {@link #ACCEPTED_OFFER_TTL}. Every transition locks the offer row.
 */
@Service
public class OfferService {

    private static final Logger logger = LoggerFactory.getLogger(OfferService.class);

    static final Duration ACCEPTED_OFFER_TTL = Duration.ofHours(48);
    /** Offers below this share of the asking price are refused as lowball offers. */
    static final BigDecimal MIN_OFFER_RATIO = new BigDecimal("0.75");

    private final OfferRepository offerRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CartService cartService;
    private final NotificationService notificationService;
    private final TransactionTemplate transactionTemplate;
    private final UserBlockService userBlockService;

    public OfferService(
            OfferRepository offerRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            CartService cartService,
            NotificationService notificationService,
            TransactionTemplate transactionTemplate,
            UserBlockService userBlockService
    ) {
        this.offerRepository = offerRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.cartService = cartService;
        this.notificationService = notificationService;
        this.transactionTemplate = transactionTemplate;
        this.userBlockService = userBlockService;
    }

    @Transactional
    public OfferDto make(Long buyerId, Long productId, BigDecimal rawAmount) {
        Product product = publicProduct(productId);
        UserEntity seller = product.getVendor().getUser();
        if (seller.getId().equals(buyerId)) {
            throw new IllegalArgumentException("Cannot make an offer on own product");
        }
        userBlockService.ensureNotBlocked(buyerId, seller.getId());
        if (seller.isVacationMode()) {
            throw new IllegalArgumentException("Seller is away");
        }
        BigDecimal amount = normalize(rawAmount);
        if (amount.compareTo(product.getPrice()) >= 0) {
            throw new IllegalArgumentException("Offer must be below the listing price");
        }
        if (amount.compareTo(minimumOffer(product.getPrice())) < 0) {
            throw new IllegalArgumentException("Offer must be at least 75% of the listing price");
        }
        if (offerRepository.existsByProduct_IdAndBuyer_IdAndStatusIn(productId, buyerId, OfferStatus.OPEN)) {
            throw new IllegalArgumentException("Offer already open for this listing");
        }
        UserEntity buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Offer offer = new Offer();
        offer.setProduct(product);
        offer.setBuyer(buyer);
        offer.setSeller(seller);
        offer.setAmount(amount);
        offer.setCurrency(product.getCurrency());
        offer.setStatus(OfferStatus.PENDING);
        Offer saved = offerRepository.save(offer);
        notificationService.createOfferNotification(seller, saved, OfferEvent.RECEIVED);
        return toDto(saved, seller.getId());
    }

    /** Seller accepts a pending offer, or buyer accepts the seller's counter. */
    @Transactional
    public OfferDto accept(Long userId, Long offerId) {
        Offer offer = lock(offerId);
        UserEntity other = answer(offer, userId);
        publicProduct(offer.getProduct().getId());
        offer.setStatus(OfferStatus.ACCEPTED);
        offer.setExpiresAt(OffsetDateTime.now().plus(ACCEPTED_OFFER_TTL));
        offerRepository.save(offer);
        notificationService.createOfferNotification(other, offer, OfferEvent.ACCEPTED);
        return toDto(offer, userId);
    }

    @Transactional
    public OfferDto decline(Long userId, Long offerId) {
        Offer offer = lock(offerId);
        UserEntity other = answer(offer, userId);
        offer.setStatus(OfferStatus.DECLINED);
        offerRepository.save(offer);
        notificationService.createOfferNotification(other, offer, OfferEvent.DECLINED);
        return toDto(offer, userId);
    }

    /** Seller proposes a price between the buyer's offer and the asking price. */
    @Transactional
    public OfferDto counter(Long sellerId, Long offerId, BigDecimal rawAmount) {
        Offer offer = lock(offerId);
        if (offer.getStatus() != OfferStatus.PENDING || !offer.getSeller().getId().equals(sellerId)) {
            throw new IllegalArgumentException("Offer cannot be answered");
        }
        Product product = publicProduct(offer.getProduct().getId());
        BigDecimal amount = normalize(rawAmount);
        if (amount.compareTo(offer.getAmount()) <= 0 || amount.compareTo(product.getPrice()) >= 0) {
            throw new IllegalArgumentException("Counter offer must be between the offer and the listing price");
        }
        offer.setAmount(amount);
        offer.setStatus(OfferStatus.COUNTERED);
        offerRepository.save(offer);
        notificationService.createOfferNotification(offer.getBuyer(), offer, OfferEvent.COUNTERED);
        return toDto(offer, sellerId);
    }

    @Transactional
    public OfferDto withdraw(Long buyerId, Long offerId) {
        Offer offer = lock(offerId);
        if (!offer.getBuyer().getId().equals(buyerId)) {
            throw new IllegalArgumentException("Offer not found");
        }
        if (!OfferStatus.OPEN.contains(offer.getStatus())) {
            throw new IllegalArgumentException("Offer cannot be answered");
        }
        // A cart line priced with this offer falls back to the listing price.
        offer.setStatus(OfferStatus.WITHDRAWN);
        offerRepository.save(offer);
        return toDto(offer, buyerId);
    }

    /** Puts the listing in the buyer's cart at the accepted price (quantity 1). */
    @Transactional
    public CartDto addToCart(Long buyerId, Long offerId) {
        Offer offer = lock(offerId);
        if (!offer.getBuyer().getId().equals(buyerId)) {
            throw new IllegalArgumentException("Offer not found");
        }
        if (!offer.isBuyable(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Offer is not accepted or has expired");
        }
        return cartService.addOfferItem(buyerId, offer);
    }

    @Transactional(readOnly = true)
    public List<OfferDto> listForBuyer(Long buyerId) {
        return offerRepository.findByBuyer_IdOrderByUpdatedAtDesc(buyerId).stream()
                .map(offer -> toDto(offer, buyerId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OfferDto> listForSeller(Long sellerId) {
        return offerRepository.findBySeller_IdOrderByUpdatedAtDesc(sellerId).stream()
                .map(offer -> toDto(offer, sellerId))
                .toList();
    }

    /** The buyer's open offer on a listing, shown on the product page. */
    @Transactional(readOnly = true)
    public OfferDto openOfferFor(Long buyerId, Long productId) {
        return offerRepository.findFirstByProduct_IdAndBuyer_IdAndStatusInOrderByCreatedAtDesc(
                        productId, buyerId, OfferStatus.OPEN)
                .map(offer -> toDto(offer, buyerId))
                .orElse(null);
    }

    @Scheduled(cron = "${app.offers.expiration-cron:0 5 * * * *}")
    public void expireAcceptedOffers() {
        for (Long offerId : offerRepository.findIdsByStatusExpiredBefore(OfferStatus.ACCEPTED, OffsetDateTime.now())) {
            try {
                // One transaction per offer (a self-call would skip @Transactional).
                transactionTemplate.executeWithoutResult(status -> expire(offerId));
            } catch (RuntimeException ex) {
                logger.warn("Could not expire offer {}: {}", offerId, ex.getMessage());
            }
        }
    }

    @Transactional
    public void expire(Long offerId) {
        Offer offer = lock(offerId);
        if (offer.getStatus() == OfferStatus.ACCEPTED && !offer.isBuyable(OffsetDateTime.now())) {
            offer.setStatus(OfferStatus.EXPIRED);
            offerRepository.save(offer);
        }
    }

    /**
     * Returns the other party after checking that {@code userId} is the one expected to answer:
     * the seller for a PENDING offer, the buyer for a COUNTERED one.
     */
    private UserEntity answer(Offer offer, Long userId) {
        if (offer.getStatus() == OfferStatus.PENDING && offer.getSeller().getId().equals(userId)) {
            return offer.getBuyer();
        }
        if (offer.getStatus() == OfferStatus.COUNTERED && offer.getBuyer().getId().equals(userId)) {
            return offer.getSeller();
        }
        if (!offer.getSeller().getId().equals(userId) && !offer.getBuyer().getId().equals(userId)) {
            throw new IllegalArgumentException("Offer not found");
        }
        throw new IllegalArgumentException("Offer cannot be answered");
    }

    private Offer lock(Long offerId) {
        return offerRepository.findByIdForUpdate(offerId)
                .orElseThrow(() -> new IllegalArgumentException("Offer not found"));
    }

    private Product publicProduct(Long productId) {
        Product product = productId == null ? null : productRepository
                .findPublicById(productId, ModerationStatus.APPROVED, OffsetDateTime.now())
                .orElse(null);
        if (product == null || product.getVendor() == null || product.getVendor().getUser() == null) {
            throw new IllegalArgumentException("Product not found");
        }
        return product;
    }

    /** Lowest acceptable offer, rounded up to the cent so it is never below the ratio. */
    static BigDecimal minimumOffer(BigDecimal listingPrice) {
        return listingPrice.multiply(MIN_OFFER_RATIO).setScale(2, RoundingMode.CEILING);
    }

    private BigDecimal normalize(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Offer amount must be positive");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private OfferDto toDto(Offer offer, Long viewerId) {
        boolean viewerIsBuyer = offer.getBuyer().getId().equals(viewerId);
        UserEntity other = viewerIsBuyer ? offer.getSeller() : offer.getBuyer();
        boolean awaitingMe = viewerIsBuyer
                ? offer.getStatus() == OfferStatus.COUNTERED
                : offer.getStatus() == OfferStatus.PENDING;
        Product product = offer.getProduct();
        String image = product.getImages().stream()
                .sorted(Comparator.comparingInt(ProductImage::getSortOrder))
                .map(ProductImage::getUrl)
                .findFirst()
                .orElse(null);
        return new OfferDto(
                offer.getId(),
                product.getId(),
                product.getName(),
                image,
                product.getPrice(),
                offer.getAmount(),
                offer.getCurrency(),
                offer.getStatus().name(),
                offer.getExpiresAt(),
                other.getId(),
                other.getDisplayName() != null && !other.getDisplayName().isBlank() ? other.getDisplayName() : null,
                awaitingMe,
                offer.getUpdatedAt()
        );
    }

    public enum OfferEvent {RECEIVED, COUNTERED, ACCEPTED, DECLINED}
}
