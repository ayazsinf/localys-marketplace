package com.localys.marketplace.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Order as seen by the buyer (all items) or by a seller (only that seller's items, plus
 * the shipping details needed to send them).
 */
public record OrderDto(
        Long id,
        String status,
        BigDecimal subtotal,
        BigDecimal shippingPrice,
        BigDecimal total,
        String currency,
        String shippingMethod,
        OffsetDateTime createdAt,
        String buyerDisplayName,
        Long buyerUserId,
        ShippingAddressDto shippingAddress,
        List<OrderItemDto> items
) {
    /**
     * @param myRating the rating the viewer (buyer or seller) gave the other party for this item,
     *                 or null when not reviewed yet
     */
    public record OrderItemDto(
            Long orderItemId,
            Long productId,
            String productName,
            String imageUrl,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            String trackingNumber,
            OffsetDateTime shippedAt,
            OffsetDateTime deliveredAt,
            Integer myRating
    ) {
    }

    public record ShippingAddressDto(
            String fullName,
            String phone,
            String line1,
            String line2,
            String postalCode,
            String city,
            String country
    ) {
    }
}
