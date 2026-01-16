package com.localys.marketplace.dto;

import java.math.BigDecimal;
import java.util.List;

public record ListingDto(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String currency,
        int stockQty,
        boolean active,
        String sku,
        String brand,
        List<String> imageUrls,
        Long categoryId,
        String categoryName,
        Long parentCategoryId,
        String locationText,
        Double latitude,
        Double longitude
) {
}
