package com.localys.marketplace.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Spots the usual signs of a classifieds scam in a message, so the recipient can be warned:
 * paying outside the platform, links to other sites, and moving the chat elsewhere.
 * It only warns; the message is still delivered.
 */
@Service
public class MessageSafetyService {

    public enum Warning {
        /** Bank transfer, cash transfer services, vouchers, crypto. */
        PAYMENT_OUTSIDE,
        /** A link to another website (fake payment or delivery pages). */
        EXTERNAL_LINK,
        /** Phone number, email address or another messaging app. */
        CONTACT_DETAILS
    }

    private static final List<String> PAYMENT_WORDS = List.of(
            "western union", "moneygram", "mandat cash", "transcash", "neosurf", "paysafecard",
            "coupon pcs", "gift card", "carte cadeau", "iban", "virement", "bank transfer", "wire transfer",
            "havale", "bitcoin", "crypto", "usdt"
    );
    private static final List<String> CONTACT_APPS = List.of("whatsapp", "telegram", "signal app", "viber");

    private static final Pattern IBAN = Pattern.compile("\\b[A-Z]{2}\\d{2}(?:\\s?[A-Z0-9]{4}){3,7}(?:\\s?[A-Z0-9]{1,3})?\\b");
    private static final Pattern EFT = Pattern.compile("\\beft\\b");
    private static final Pattern LINK = Pattern.compile(
            "(?:https?://|www\\.)\\S+|\\b[a-z0-9-]+\\.(?:com|net|org|fr|tr|io|ly|me|info|xyz|site|online|shop)(?:/\\S*)?\\b");
    private static final Pattern EMAIL = Pattern.compile("[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?:\\+|\\b00)?\\d(?:[\\s.\\-]?\\d){8,13}\\b");

    /** The most serious warning for this text, or null when it looks fine. */
    public Warning check(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (PAYMENT_WORDS.stream().anyMatch(lower::contains)
                || EFT.matcher(lower).find()
                || IBAN.matcher(text.toUpperCase(Locale.ROOT)).find()) {
            return Warning.PAYMENT_OUTSIDE;
        }
        // Emails contain a domain, so look for them before links.
        boolean hasEmail = EMAIL.matcher(lower).find();
        if (!hasEmail && LINK.matcher(lower).find()) {
            return Warning.EXTERNAL_LINK;
        }
        if (hasEmail || CONTACT_APPS.stream().anyMatch(lower::contains) || PHONE.matcher(text).find()) {
            return Warning.CONTACT_DETAILS;
        }
        return null;
    }
}
