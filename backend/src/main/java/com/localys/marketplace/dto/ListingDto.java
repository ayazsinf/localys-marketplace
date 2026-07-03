package com.localys.marketplace.dto;

import java.math.BigDecimal;
import java.util.List;

public record ListingDto(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String country,
        String currency,
        int stockQty,
        boolean active,
        String removalReason,
        String removalNote,
        java.time.OffsetDateTime removedAt,
        java.time.OffsetDateTime expiresAt,
        String moderationStatus,
        String moderationReason,
        String sku,
        String brand,
        List<String> imageUrls,
        Long categoryId,
        String categoryName,
        Long parentCategoryId,
        List<Long> categoryPathIds,
        List<String> categoryPathNames,
        String locationText,
        Double latitude,
        Double longitude
) {
}
