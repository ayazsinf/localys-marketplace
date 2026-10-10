package com.localys.marketplace.dto;

public record ReviewCreateRequest(
        Long orderItemId,
        Integer rating,
        String comment
) {
}
