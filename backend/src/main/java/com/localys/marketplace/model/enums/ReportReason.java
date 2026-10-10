package com.localys.marketplace.model.enums;

import java.util.Locale;
import java.util.Optional;

public enum ReportReason {
    SCAM,
    PROHIBITED_ITEM,
    MISLEADING,
    OFFENSIVE,
    WRONG_CATEGORY,
    OTHER;

    public static Optional<ReportReason> from(String raw) {
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
