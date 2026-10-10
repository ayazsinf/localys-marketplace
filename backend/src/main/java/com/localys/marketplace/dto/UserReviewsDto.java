package com.localys.marketplace.dto;

/**
 * A user's ratings, overall and split by the role they had in the sale, with one page of reviews.
 */
public record UserReviewsDto(
        RatingDto overall,
        RatingDto asSeller,
        RatingDto asBuyer,
        PageResponse<ReviewDto> reviews
) {
    public record RatingDto(double average, long count) {
    }
}
