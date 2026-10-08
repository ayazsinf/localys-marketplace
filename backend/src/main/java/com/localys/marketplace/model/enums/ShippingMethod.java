package com.localys.marketplace.model.enums;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

/**
 * Shipping options offered at checkout. Prices are owned by the backend so the
 * client cannot choose what it pays for delivery.
 */
public enum ShippingMethod {
    MONDIAL(new BigDecimal("4.90")),
    CHRONOPOST(new BigDecimal("8.90")),
    COLISSIMO(new BigDecimal("6.50"));

    private final BigDecimal price;

    ShippingMethod(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal price() {
        return price;
    }

    public static Optional<ShippingMethod> from(String raw) {
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
