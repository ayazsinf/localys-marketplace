package com.localys.marketplace.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrderCreateRequest(
        List<Long> productIds,
        Long addressId,
        String shippingMethod,
        BigDecimal shippingPrice,
        String paymentMethod
) {
}
