package com.localys.marketplace.service;

import com.localys.marketplace.model.Favorite;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.enums.ModerationStatus;
import com.localys.marketplace.repository.FavoriteRepository;
import com.localys.marketplace.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Tells users when a listing they favorited gets cheaper. Each favorite remembers the price
 * the user last saw, so a drop is only announced once and a later increase resets the baseline.
 * Called whenever a listing may have become visible at a new price (seller edit, admin approval),
 * since a price change can send the listing back to moderation first.
 */
@Service
public class PriceDropService {

    private final ProductRepository productRepository;
    private final FavoriteRepository favoriteRepository;
    private final NotificationService notificationService;

    public PriceDropService(
            ProductRepository productRepository,
            FavoriteRepository favoriteRepository,
            NotificationService notificationService
    ) {
        this.productRepository = productRepository;
        this.favoriteRepository = favoriteRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public void notifyIfPriceDropped(Long productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null || !isVisible(product)) {
            return;
        }
        BigDecimal price = product.getPrice();
        String currency = product.getCurrency();
        Long ownerId = product.getVendor() != null && product.getVendor().getUser() != null
                ? product.getVendor().getUser().getId()
                : null;
        for (Favorite favorite : favoriteRepository.findByProductId(productId)) {
            BigDecimal lastSeen = favorite.getLastSeenPrice();
            if (lastSeen != null && lastSeen.compareTo(price) == 0
                    && Objects.equals(favorite.getLastSeenCurrency(), currency)) {
                continue;
            }
            boolean dropped = lastSeen != null
                    && Objects.equals(favorite.getLastSeenCurrency(), currency)
                    && price.compareTo(lastSeen) < 0;
            if (dropped && !favorite.getUser().getId().equals(ownerId)) {
                notificationService.createPriceDropNotification(favorite.getUser(), product, lastSeen);
            }
            favorite.setLastSeenPrice(price);
            favorite.setLastSeenCurrency(currency);
            favoriteRepository.save(favorite);
        }
    }

    private boolean isVisible(Product product) {
        return product.isActive()
                && product.getModerationStatus() == ModerationStatus.APPROVED
                && product.getExpiresAt() != null
                && product.getExpiresAt().isAfter(OffsetDateTime.now());
    }
}
