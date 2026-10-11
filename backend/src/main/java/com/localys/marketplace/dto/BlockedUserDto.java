package com.localys.marketplace.dto;

import java.time.OffsetDateTime;

public record BlockedUserDto(Long userId, String displayName, OffsetDateTime blockedAt) {
}
