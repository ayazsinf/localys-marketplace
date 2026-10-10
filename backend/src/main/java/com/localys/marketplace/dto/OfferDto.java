package com.localys.marketplace.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * An offer as seen by the buyer or the seller.
 *
 * @param otherUserId   the seller for the buyer, the buyer for the seller
 * @param awaitingMe    true when the viewer is the one who has to answer
 */
public record OfferDto(
        Long id,
        Long productId,
        String productName,
        String productImageUrl,
        BigDecimal listingPrice,
        BigDecimal amount,
        String currency,
        String status,
        OffsetDateTime expiresAt,
        Long otherUserId,
        String otherUserName,
        boolean awaitingMe,
        OffsetDateTime updatedAt
) {
}
