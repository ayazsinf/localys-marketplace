package com.localys.marketplace.repository;

import com.localys.marketplace.model.Cart;
import com.localys.marketplace.model.enums.CartStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {
    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<Cart> findByUserIdAndStatus(Long userId, CartStatus status);
}
