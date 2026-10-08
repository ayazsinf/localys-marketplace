package com.localys.marketplace.dto;

import java.util.List;

/**
 * Shipping price is intentionally not part of the request: it is resolved on the
 * server from {@code shippingMethod}.
 */
public record OrderCreateRequest(
        List<Long> productIds,
        Long addressId,
        String shippingMethod,
        String paymentMethod
) {
}
