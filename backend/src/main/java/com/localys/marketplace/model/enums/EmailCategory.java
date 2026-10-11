package com.localys.marketplace.model.enums;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Kinds of email a user can switch off. In-app notifications are always kept. */
public enum EmailCategory {
    /** First unread message of a conversation. */
    MESSAGES,
    OFFERS,
    /** Sales, payments, shipping and delivery. */
    ORDERS,
    /** Moderation of the user's own listings and reports about them. */
    LISTINGS,
    /** Favorites and reviews from other members. */
    ACTIVITY,
    /** Saved-search matches and price drops. */
    ALERTS;

    public static EmailCategory of(NotificationType type) {
        return switch (type) {
            case OFFER_RECEIVED, OFFER_COUNTERED, OFFER_ACCEPTED, OFFER_DECLINED -> OFFERS;
            case ORDER_PAID, ORDER_SOLD, ORDER_SHIPPED, ORDER_DELIVERED,
                 DISPUTE_OPENED, DISPUTE_CLOSED, ORDER_REFUNDED, PAYMENT_RELEASED -> ORDERS;
            case PRODUCT_CREATED, PRODUCT_PENDING_REVIEW, PRODUCT_APPROVED, PRODUCT_REJECTED, LISTING_REPORTED -> LISTINGS;
            case FAVORITE_ADDED, REVIEW_RECEIVED -> ACTIVITY;
            case PRICE_DROP, SAVED_SEARCH_MATCH -> ALERTS;
        };
    }

    /** Parses the comma-separated column; unknown names are ignored. */
    public static Set<EmailCategory> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return EnumSet.noneOf(EmailCategory.class);
        }
        Set<EmailCategory> result = EnumSet.noneOf(EmailCategory.class);
        for (String part : raw.split(",")) {
            Arrays.stream(values())
                    .filter(category -> category.name().equals(part.trim().toUpperCase(Locale.ROOT)))
                    .findFirst()
                    .ifPresent(result::add);
        }
        return result;
    }

    public static String format(Set<EmailCategory> categories) {
        return categories == null || categories.isEmpty()
                ? null
                : categories.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }
}
