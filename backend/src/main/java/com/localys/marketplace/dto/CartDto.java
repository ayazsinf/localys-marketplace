package com.localys.marketplace.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartDto(
        Long id,
        String status,
        List<CartItemDto> items,
        BigDecimal total
){
}
