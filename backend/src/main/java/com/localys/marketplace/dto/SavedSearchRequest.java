package com.localys.marketplace.dto;

import java.math.BigDecimal;

public record SavedSearchRequest(
        String name,
        String query,
        Long categoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean alertsEnabled
) {
}
