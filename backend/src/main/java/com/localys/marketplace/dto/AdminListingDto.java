package com.localys.marketplace.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record AdminListingDto(
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
        OffsetDateTime removedAt,
        OffsetDateTime expiresAt,
        String moderationStatus,
        String moderationReason,
        String sku,
        String brand,
        List<String> imageUrls,
        Long categoryId,
        String categoryName,
        Long vendorId,
        Long vendorUserId,
        String vendorDisplayName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime reviewedAt
) {
}
