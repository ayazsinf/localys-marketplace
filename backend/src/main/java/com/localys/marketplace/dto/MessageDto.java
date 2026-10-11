package com.localys.marketplace.dto;

import java.time.OffsetDateTime;

public record MessageDto(
        Long id,
        Long conversationId,
        Long senderId,
        String body,
        OffsetDateTime createdAt,
        /** Shown to the recipient as a safety tip; null for ordinary messages. */
        String safetyWarning,
        /** When the recipient read it; null while unread. */
        OffsetDateTime readAt
) {
}
