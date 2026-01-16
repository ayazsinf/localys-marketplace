package com.localys.marketplace.dto;

import java.time.OffsetDateTime;

public record ConversationDto(
        Long id,
        Long otherUserId,
        String otherUsername,
        String otherDisplayName,
        String lastMessage,
        OffsetDateTime lastMessageAt
) {
}
