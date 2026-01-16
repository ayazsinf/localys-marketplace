package com.localys.marketplace.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record ProductDetailDto(
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
        Long vendorId,
        Long vendorUserId,
        String vendorDisplayName,
        String vendorShopName,
        OffsetDateTime createdAt,
        String locationText,
        Double latitude,
        Double longitude
) {
}
