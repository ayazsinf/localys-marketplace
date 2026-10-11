package com.localys.marketplace.model.enums;

import java.util.EnumSet;
import java.util.Set;

public enum DisputeStatus {
    /** The buyer reported a problem; the seller can refund or contest. */
    OPEN,
    /** The seller disagrees; an admin decides. */
    CONTESTED,
    /** The buyer withdrew it; the money stays held as before. */
    WITHDRAWN,
    /** Closed with a refund to the buyer. */
    REFUNDED,
    /** Closed in the seller's favour; the money is released. */
    RELEASED;

    public static final Set<DisputeStatus> ACTIVE = EnumSet.of(OPEN, CONTESTED);
}
