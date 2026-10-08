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
        ShippingAddressDto shippingAddress,
        List<OrderItemDto> items
) {
    public record OrderItemDto(
            Long productId,
            String productName,
            String imageUrl,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            String trackingNumber,
            OffsetDateTime shippedAt,
            OffsetDateTime deliveredAt
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
