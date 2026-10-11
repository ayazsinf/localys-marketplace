package com.localys.marketplace.dto;

import java.time.OffsetDateTime;

/**
 * Live conversation activity sent on {@code /topic/conversations/{id}/events}.
 *
 * @param type   READ (userId read everything sent to them up to {@code at}) or TYPING
 * @param userId who read or is typing
 */
public record ConversationEventDto(String type, Long conversationId, Long userId, OffsetDateTime at) {
}
