package com.localys.marketplace.dto;

public record CartItemRequest(
        Long productId,
        Integer quantity
) {
}
