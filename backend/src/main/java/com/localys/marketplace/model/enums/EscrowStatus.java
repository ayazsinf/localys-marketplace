package com.localys.marketplace.model.enums;

/** Where the money for one sold item stands under buyer protection. */
public enum EscrowStatus {
    /** Paid and kept by the platform until the buyer confirms receipt. */
    HELD,
    /** Held while a dispute about the item is open. */
    DISPUTED,
    /** Due to the seller. */
    RELEASED,
    /** Given back to the buyer. */
    REFUNDED
}
