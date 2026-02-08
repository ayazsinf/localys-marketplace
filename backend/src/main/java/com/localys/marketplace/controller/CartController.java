package com.localys.marketplace.controller;

import com.localys.marketplace.dto.CartDto;
import com.localys.marketplace.dto.CartItemRequest;
import com.localys.marketplace.model.CustomUserDetails;
import com.localys.marketplace.service.CartService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<CartDto> getCart(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(cartService.getActiveCart(userDetails.getUserId()));
    }

    @PostMapping("/items")
    public ResponseEntity<CartDto> addItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CartItemRequest request
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        int quantity = request.quantity() != null ? request.quantity() : 1;
        return ResponseEntity.ok(cartService.addItem(userDetails.getUserId(), request.productId(), quantity));
    }

    @PutMapping("/items/{productId}")
    public ResponseEntity<CartDto> updateItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("productId") Long productId,
            @RequestBody CartItemRequest request
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        int quantity = request.quantity() != null ? request.quantity() : 1;
        return ResponseEntity.ok(cartService.updateQuantity(userDetails.getUserId(), productId, quantity));
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<CartDto> removeItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("productId") Long productId
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(cartService.removeItem(userDetails.getUserId(), productId));
    }
}
