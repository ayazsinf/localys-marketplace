package com.localys.marketplace.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductListDto(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String country,
        String currency,
        boolean inStock,
        Long categoryId,
        String categoryName,
        List<Long> categoryPathIds,
        List<String> categoryPathNames,
        Long vendorUserId,
        List<String> imageUrls
) {
}

