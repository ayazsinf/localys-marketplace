package com.localys.marketplace.dto;

import java.time.OffsetDateTime;

public record ReviewDto(
        Long id,
        int rating,
        String comment,
        String reviewerName,
        String productName,
        OffsetDateTime createdAt
) {
}
