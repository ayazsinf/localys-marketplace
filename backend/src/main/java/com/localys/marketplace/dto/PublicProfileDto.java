package com.localys.marketplace.dto;

import java.time.OffsetDateTime;

/**
 * What anyone can see about a user, like a classifieds seller page: no email, phone or address.
 *
 * @param location     city of their most recent active listing, if any
 * @param responseRate share (0-100) of conversations started by others that they answered in
 *                     the last 90 days; null without enough conversations
 * @param responseTime WITHIN_HOUR, WITHIN_DAY, WITHIN_DAYS or null
 */
public record PublicProfileDto(
        Long id,
        String displayName,
        OffsetDateTime memberSince,
        String location,
        long activeListingCount,
        UserReviewsDto.RatingDto rating,
        UserReviewsDto.RatingDto ratingAsSeller,
        UserReviewsDto.RatingDto ratingAsBuyer,
        Integer responseRate,
        String responseTime
) {
}
