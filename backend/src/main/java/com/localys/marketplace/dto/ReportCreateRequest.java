package com.localys.marketplace.dto;

public record ReportCreateRequest(
        Long productId,
        String reason,
        String details
) {
}
