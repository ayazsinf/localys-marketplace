package com.localys.marketplace.dto;

import java.math.BigDecimal;

public record CartItemDto(
        Long productId,
        String productName,
        String brand,
        String imageUrl,
        BigDecimal unitPrice,
        String currency,
        Integer stockQty,
        int quantity,
        BigDecimal lineTotal,
        String availability,
        /** Priced with an accepted offer (quantity fixed at 1). */
        boolean offerApplied
) {
}
