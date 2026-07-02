package com.localys.marketplace.event;

import com.localys.marketplace.model.enums.ProductModerationEventType;

public record ProductModerationEvent(Long productId, ProductModerationEventType type) {
}
