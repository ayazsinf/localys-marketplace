package com.localys.marketplace.dto;

import java.time.OffsetDateTime;

public record MessageDto(
        Long id,
        Long conversationId,
        Long senderId,
        String body,
        OffsetDateTime createdAt
) {
}
