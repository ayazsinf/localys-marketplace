package com.localys.marketplace.model.enums;

import java.util.Locale;
import java.util.Optional;

/** Condition of a second-hand item, from brand new to only good for parts. */
public enum ItemCondition {
    NEW,
    LIKE_NEW,
    GOOD,
    FAIR,
    FOR_PARTS;

    public static Optional<ItemCondition> from(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
