package com.localys.marketplace.service;

import com.localys.marketplace.dto.CartDto;
import com.localys.marketplace.dto.CartItemDto;
import com.localys.marketplace.model.Cart;
import com.localys.marketplace.model.CartItem;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.ProductImage;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.enums.CartStatus;
import com.localys.marketplace.repository.CartItemRepository;
import com.localys.marketplace.repository.CartRepository;
import com.localys.marketplace.repository.ProductRepository;
import com.localys.marketplace.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            UserRepository userRepository
    ) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CartDto getActiveCart(Long userId) {
        Cart cart = getOrCreateActiveCart(userId);
        return toDto(cart);
    }

    @Transactional
    public CartDto addItem(Long userId, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        Cart cart = getOrCreateActiveCart(userId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        if (product.getVendor() != null
                && product.getVendor().getUser() != null
                && userId.equals(product.getVendor().getUser().getId())) {
            throw new IllegalArgumentException("Cannot add own product");
        }
        int stockQty = product.getStockQty();
        if (stockQty <= 0) {
            throw new IllegalArgumentException("Product out of stock");
        }

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseGet(() -> {
                    CartItem newItem = new CartItem();
                    newItem.setCart(cart);
                    newItem.setProduct(product);
                    newItem.setQuantity(0);
                    newItem.setUnitPriceSnapshot(product.getPrice());
                    newItem.setCurrency(product.getCurrency());
                    cart.getItems().add(newItem);
                    return newItem;
                });
        int desiredQty = item.getQuantity() + quantity;
        if (desiredQty > stockQty) {
            desiredQty = stockQty;
        }
        item.setQuantity(desiredQty);
        cartRepository.save(cart);
        return toDto(cart);
    }

    @Transactional
    public CartDto updateQuantity(Long userId, Long productId, int quantity) {
        Cart cart = getOrCreateActiveCart(userId);
        if (quantity <= 0) {
            cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                    .ifPresent(item -> {
                        cartItemRepository.delete(item);
                        cart.getItems().remove(item);
                    });
            return toDto(reloadCart(userId, cart));
        }

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found"));
        if (item.getProduct().getVendor() != null
                && item.getProduct().getVendor().getUser() != null
                && userId.equals(item.getProduct().getVendor().getUser().getId())) {
            throw new IllegalArgumentException("Cannot add own product");
        }
        int stockQty = item.getProduct().getStockQty();
        if (stockQty <= 0) {
            cartItemRepository.delete(item);
            cart.getItems().remove(item);
            return toDto(reloadCart(userId, cart));
        }
        int desiredQty = quantity > stockQty ? stockQty : quantity;
        item.setQuantity(desiredQty);
        cartItemRepository.save(item);
        return toDto(reloadCart(userId, cart));
    }

    @Transactional
    public CartDto removeItem(Long userId, Long productId) {
        Cart cart = getOrCreateActiveCart(userId);
        cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .ifPresent(item -> {
                    cartItemRepository.delete(item);
                    cart.getItems().remove(item);
                });
        return toDto(reloadCart(userId, cart));
    }

    private Cart getOrCreateActiveCart(Long userId) {
        return cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseGet(() -> {
                    UserEntity user = userRepository.findById(userId)
                            .orElseThrow(() -> new IllegalArgumentException("User not found"));
                    Cart cart = new Cart();
                    cart.setUser(user);
                    cart.setStatus(CartStatus.ACTIVE);
                    return cartRepository.save(cart);
                });
    }

    private CartDto toDto(Cart cart) {
        List<CartItemDto> items = cart.getItems().stream()
                .map(this::toItemDto)
                .toList();
        BigDecimal total = items.stream()
                .map(CartItemDto::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartDto(cart.getId(), cart.getStatus().name(), items, total);
    }

    private CartItemDto toItemDto(CartItem item) {
        Product product = item.getProduct();
        String imageUrl = product.getImages().stream()
                .sorted(Comparator.comparingInt(ProductImage::getSortOrder))
                .map(ProductImage::getUrl)
                .findFirst()
                .orElse(null);
        BigDecimal unitPrice = item.getUnitPriceSnapshot();
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
        return new CartItemDto(
                product.getId(),
                product.getName(),
                product.getBrand(),
                imageUrl,
                unitPrice,
                item.getCurrency(),
                product.getStockQty(),
                item.getQuantity(),
                lineTotal
        );
    }

    private Cart reloadCart(Long userId, Cart fallback) {
        return cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElse(fallback);
    }
}
