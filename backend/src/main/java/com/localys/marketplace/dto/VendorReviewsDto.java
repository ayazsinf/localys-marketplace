package com.localys.marketplace.dto;

/**
 * A seller's rating summary and one page of their most recent reviews.
 */
public record VendorReviewsDto(
        double averageRating,
        long reviewCount,
        PageResponse<ReviewDto> reviews
) {
}
