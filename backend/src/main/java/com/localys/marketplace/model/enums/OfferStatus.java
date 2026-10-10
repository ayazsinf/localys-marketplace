package com.localys.marketplace.model.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * PENDING waits for the seller, COUNTERED waits for the buyer. An ACCEPTED offer can be bought
 * at its price until it expires; PURCHASED once that order is paid.
 */
public enum OfferStatus {
    PENDING,
    COUNTERED,
    ACCEPTED,
    DECLINED,
    WITHDRAWN,
    EXPIRED,
    PURCHASED;

    public static final Set<OfferStatus> OPEN = EnumSet.of(PENDING, COUNTERED, ACCEPTED);
}
