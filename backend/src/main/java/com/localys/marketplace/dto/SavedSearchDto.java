package com.localys.marketplace.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SavedSearchDto(
        Long id,
        String name,
        String query,
        Long categoryId,
        String categoryName,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        boolean alertsEnabled,
        OffsetDateTime createdAt
) {
}
