package com.localys.marketplace.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductListDto(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String currency,
        boolean inStock,
        String categoryName,
        Long vendorUserId,
        List<String> imageUrls
) {
}

