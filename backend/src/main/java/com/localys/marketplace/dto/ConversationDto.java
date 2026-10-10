package com.localys.marketplace.dto;

import java.time.OffsetDateTime;

public record ConversationDto(
        Long id,
        Long otherUserId,
        String otherDisplayName,
        String lastMessage,
        OffsetDateTime lastMessageAt,
        Long productId,
        String productName,
        String productImageUrl,
        long unreadCount
) {
}
