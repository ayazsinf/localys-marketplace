package com.localys.marketplace.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String status,
        BigDecimal subtotal,
        BigDecimal shippingPrice,
        BigDecimal total,
        String currency,
        OffsetDateTime createdAt,
        List<OrderItemResponse> items
) {
    public record OrderItemResponse(
            Long productId,
            String productName,
            int quantity,
            BigDecimal lineTotal
    ) {
    }
}
