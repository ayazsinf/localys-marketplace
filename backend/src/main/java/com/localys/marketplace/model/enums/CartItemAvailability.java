package com.localys.marketplace.model.enums;

/**
 * Why a cart line can or cannot be checked out. Unavailable lines stay in the cart
 * (so the buyer understands what happened) but are excluded from totals and checkout.
 */
public enum CartItemAvailability {
    AVAILABLE,
    EXPIRED,
    SOLD_OUT,
    UNAVAILABLE
}
