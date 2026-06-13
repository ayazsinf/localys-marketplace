package com.localys.marketplace.util;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MarketCountryResolver {

    private static final String DEFAULT_COUNTRY = "FR";
    private static final Map<String, String> COUNTRY_TO_CURRENCY = Map.ofEntries(
            Map.entry("FR", "EUR"),
            Map.entry("DE", "EUR"),
            Map.entry("ES", "EUR"),
            Map.entry("IT", "EUR"),
            Map.entry("BE", "EUR"),
            Map.entry("NL", "EUR"),
            Map.entry("PT", "EUR"),
            Map.entry("IE", "EUR"),
            Map.entry("AT", "EUR"),
            Map.entry("TR", "TRY"),
            Map.entry("GB", "GBP"),
            Map.entry("US", "USD"),
            Map.entry("CA", "CAD"),
            Map.entry("CH", "CHF")
    );
    private static final Map<String, String> CURRENCY_TO_COUNTRY = Map.of(
            "TRY", "TR",
            "USD", "US",
            "GBP", "GB",
            "CAD", "CA",
            "CHF", "CH",
            "EUR", "FR"
    );

    private MarketCountryResolver() {
    }

    public static String resolveCountry(String country, String currency) {
        String normalizedCountry = normalize(country, 2);
        if (normalizedCountry != null && COUNTRY_TO_CURRENCY.containsKey(normalizedCountry)) {
            return normalizedCountry;
        }

        String normalizedCurrency = normalize(currency, 3);
        if (normalizedCurrency != null && CURRENCY_TO_COUNTRY.containsKey(normalizedCurrency)) {
            return CURRENCY_TO_COUNTRY.get(normalizedCurrency);
        }

        return DEFAULT_COUNTRY;
    }

    public static String resolveCurrency(String country) {
        String normalizedCountry = normalize(country, 2);
        if (normalizedCountry == null) {
            return COUNTRY_TO_CURRENCY.get(DEFAULT_COUNTRY);
        }
        return COUNTRY_TO_CURRENCY.getOrDefault(normalizedCountry, COUNTRY_TO_CURRENCY.get(DEFAULT_COUNTRY));
    }

    public static Set<String> supportedCountries() {
        return COUNTRY_TO_CURRENCY.keySet();
    }

    private static String normalize(String value, int expectedLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != expectedLength) {
            return null;
        }
        return normalized;
    }
}
