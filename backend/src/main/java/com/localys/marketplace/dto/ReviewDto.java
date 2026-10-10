package com.localys.marketplace.dto;

import java.time.OffsetDateTime;

/**
 * @param revieweeRole SELLER when the buyer wrote it, BUYER when the seller did
 */
public record ReviewDto(
        Long id,
        int rating,
        String comment,
        Long reviewerId,
        String reviewerName,
        String revieweeRole,
        String productName,
        OffsetDateTime createdAt
) {
}
