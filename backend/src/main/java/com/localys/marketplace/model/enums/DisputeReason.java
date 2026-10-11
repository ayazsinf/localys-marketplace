package com.localys.marketplace.model.enums;

import java.util.Locale;
import java.util.Optional;

public enum DisputeReason {
    NOT_RECEIVED,
    NOT_AS_DESCRIBED,
    DAMAGED,
    OTHER;

    public static Optional<DisputeReason> from(String raw) {
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
