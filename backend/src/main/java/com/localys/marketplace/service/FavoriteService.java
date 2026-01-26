package com.localys.marketplace.service;

import com.localys.marketplace.model.Favorite;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.repository.FavoriteRepository;
import com.localys.marketplace.repository.ProductRepository;
import com.localys.marketplace.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public FavoriteService(
            FavoriteRepository favoriteRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            NotificationService notificationService
    ) {
        this.favoriteRepository = favoriteRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    public List<Product> getFavoriteProducts(Long userId) {
        return favoriteRepository.findByUserId(userId).stream()
                .map(Favorite::getProduct)
                .toList();
    }

    public List<Long> getFavoriteProductIds(Long userId) {
        return favoriteRepository.findByUserId(userId).stream()
                .map(favorite -> favorite.getProduct().getId())
                .toList();
    }

    @Transactional(noRollbackFor = DataIntegrityViolationException.class)
    public void addFavorite(Long userId, Long productId) {
        if (favoriteRepository.existsByUserIdAndProductId(userId, productId)) {
            return;
        }
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        if (product.getVendor() != null
                && product.getVendor().getUser() != null
                && userId.equals(product.getVendor().getUser().getId())) {
            throw new IllegalArgumentException("Cannot favorite own product");
        }
        Favorite favorite = new Favorite();
        favorite.setUser(user);
        favorite.setProduct(product);
        try {
            favoriteRepository.save(favorite);
        } catch (DataIntegrityViolationException ex) {
            // Favori zaten varsa tekrar eklemeyi sessizce yoksay.
            return;
        }
        try {
            notifyVendorFavoriteAdded(user, product);
        } catch (RuntimeException ex) {
            // Bildirim hatasi favori kaydini engellemesin.
        }
    }

    @Transactional
    public void removeFavorite(Long userId, Long productId) {
        favoriteRepository.deleteByUserIdAndProductId(userId, productId);
    }

    private void notifyVendorFavoriteAdded(UserEntity actor, Product product) {
        if (product.getVendor() == null || product.getVendor().getUser() == null) {
            return;
        }
        UserEntity vendorUser = product.getVendor().getUser();
        if (vendorUser.getId().equals(actor.getId())) {
            return;
        }
        notificationService.createFavoriteAddedNotification(vendorUser, actor, product);
    }
}
