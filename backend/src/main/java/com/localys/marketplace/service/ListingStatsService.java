package com.localys.marketplace.service;

import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.enums.OfferStatus;
import com.localys.marketplace.repository.ConversationRepository;
import com.localys.marketplace.repository.FavoriteRepository;
import com.localys.marketplace.repository.OfferRepository;
import com.localys.marketplace.repository.ProductCount;
import com.localys.marketplace.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Seller-facing listing statistics and "renew": bringing a listing back to the top of the
 * newest-first results, at most once every {@link #RENEW_INTERVAL}.
 */
@Service
public class ListingStatsService {

    static final Duration RENEW_INTERVAL = Duration.ofDays(7);

    private final ProductRepository productRepository;
    private final FavoriteRepository favoriteRepository;
    private final ConversationRepository conversationRepository;
    private final OfferRepository offerRepository;

    public ListingStatsService(
            ProductRepository productRepository,
            FavoriteRepository favoriteRepository,
            ConversationRepository conversationRepository,
            OfferRepository offerRepository
    ) {
        this.productRepository = productRepository;
        this.favoriteRepository = favoriteRepository;
        this.conversationRepository = conversationRepository;
        this.offerRepository = offerRepository;
    }

    public record Stats(long favorites, long conversations, long openOffers) {
        static final Stats EMPTY = new Stats(0, 0, 0);
    }

    @Transactional(readOnly = true)
    public Map<Long, Stats> statsFor(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> favorites = toMap(favoriteRepository.countByProduct(productIds));
        Map<Long, Long> conversations = toMap(conversationRepository.countByProduct(productIds));
        Map<Long, Long> offers = toMap(offerRepository.countByProduct(productIds, OfferStatus.OPEN));
        return productIds.stream().distinct().collect(Collectors.toMap(
                id -> id,
                id -> new Stats(
                        favorites.getOrDefault(id, 0L),
                        conversations.getOrDefault(id, 0L),
                        offers.getOrDefault(id, 0L))
        ));
    }

    public Stats empty() {
        return Stats.EMPTY;
    }

    /** When the listing can next be renewed (now or in the past means it can be renewed now). */
    public OffsetDateTime renewableAt(Product product) {
        return product.getListedAt().plus(RENEW_INTERVAL);
    }

    /**
     * Counts a detail-page view. Runs in its own transaction so the public, read-only product
     * request can still record it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordView(Long productId) {
        productRepository.incrementViewCount(productId);
    }

    private Map<Long, Long> toMap(List<ProductCount> counts) {
        return counts.stream().collect(Collectors.toMap(ProductCount::getProductId, ProductCount::getCount));
    }
}
