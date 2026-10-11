package com.localys.marketplace.model.enums;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * Shipping options offered at checkout. Prices are owned by the backend so the
 * client cannot choose what it pays for delivery.
 */
public enum ShippingMethod {
    MONDIAL(new BigDecimal("4.90"), "https://www.mondialrelay.fr/suivi-de-colis/?numeroExpedition="),
    CHRONOPOST(new BigDecimal("8.90"), "https://www.chronopost.fr/tracking-no-cms/suivi-page?listeNumerosLT="),
    COLISSIMO(new BigDecimal("6.50"), "https://www.laposte.fr/outils/suivre-vos-envois?code=");

    private final BigDecimal price;
    private final String trackingUrlPrefix;

    ShippingMethod(BigDecimal price, String trackingUrlPrefix) {
        this.price = price;
        this.trackingUrlPrefix = trackingUrlPrefix;
    }

    public BigDecimal price() {
        return price;
    }

    /** The carrier's public tracking page for a parcel. */
    public String trackingUrl(String trackingNumber) {
        return trackingUrlPrefix + URLEncoder.encode(trackingNumber, StandardCharsets.UTF_8);
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
