package com.localys.marketplace.dto;

import java.math.BigDecimal;
import java.util.List;

public record SavedSearchRequest(
        String name,
        String query,
        Long categoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        List<String> conditions,
        String delivery,
        Double latitude,
        Double longitude,
        Double radiusKm,
        String locationLabel,
        Boolean alertsEnabled
) {
}
