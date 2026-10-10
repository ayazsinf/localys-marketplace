package com.localys.marketplace.dto;

import java.time.OffsetDateTime;

/**
 * @param openReportsForListing how many open reports the listing has in total, so admins can
 *                              spot listings flagged by several users
 */
public record AdminReportDto(
        Long id,
        Long productId,
        String productName,
        boolean productActive,
        String reason,
        String details,
        String reporterName,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime resolvedAt,
        long openReportsForListing
) {
}
