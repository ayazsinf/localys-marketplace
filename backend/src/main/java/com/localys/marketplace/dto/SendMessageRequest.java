package com.localys.marketplace.dto;

public record SendMessageRequest(
        Long conversationId,
        Long recipientId,
        String body
) {
}
