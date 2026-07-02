package com.localys.marketplace.dto;

public record CategoryDto(
        Long id,
        String name,
        String slug,
        Long parentId,
        int sortOrder
) {
}
