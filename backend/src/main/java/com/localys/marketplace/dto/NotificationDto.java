package com.localys.marketplace.dto;

import java.time.OffsetDateTime;

public record NotificationDto(
        Long id,
        String type,
        String title,
        String message,
        String link,
        boolean read,
        OffsetDateTime createdAt
) {
}
