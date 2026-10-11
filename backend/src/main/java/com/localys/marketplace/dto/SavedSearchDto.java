package com.localys.marketplace.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record SavedSearchDto(
        Long id,
        String name,
        String query,
        Long categoryId,
        String categoryName,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        List<String> conditions,
        String delivery,
        Double latitude,
        Double longitude,
        Double radiusKm,
        String locationLabel,
        boolean alertsEnabled,
        OffsetDateTime createdAt
) {
}
