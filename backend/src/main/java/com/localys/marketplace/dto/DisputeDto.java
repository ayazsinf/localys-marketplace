package com.localys.marketplace.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record DisputeDto(
        Long id,
        Long orderId,
        Long orderItemId,
        Long productId,
        String productName,
        BigDecimal amount,
        String currency,
        String reason,
        String description,
        String status,
        String sellerResponse,
        String resolutionNote,
        String buyerName,
        String sellerName,
        OffsetDateTime createdAt,
        OffsetDateTime resolvedAt
) {
}
