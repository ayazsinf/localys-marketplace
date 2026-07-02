package com.localys.marketplace.dto;

import java.util.List;

public record CategoryTreeDto(
        Long id,
        String name,
        String slug,
        Long parentId,
        List<Long> pathIds,
        List<String> pathNames,
        List<CategoryTreeDto> children
) {
}
