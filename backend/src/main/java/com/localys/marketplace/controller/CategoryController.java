package com.localys.marketplace.controller;

import com.localys.marketplace.dto.CategoryDto;
import com.localys.marketplace.dto.CategoryTreeDto;
import com.localys.marketplace.model.Category;
import com.localys.marketplace.repository.CategoryRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;

    public CategoryController(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public List<CategoryDto> listRootCategories() {
        return categoryRepository.findByParentIsNullOrderBySortOrderAscNameAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/tree")
    @Transactional(readOnly = true)
    public List<CategoryTreeDto> listTree() {
        List<Category> categories = categoryRepository.findAllByOrderBySortOrderAscNameAsc();
        Map<Long, List<Category>> byParentId = categories.stream()
                .filter(category -> category.getParent() != null)
                .collect(Collectors.groupingBy(category -> category.getParent().getId()));

        return categories.stream()
                .filter(category -> category.getParent() == null)
                .sorted(categoryComparator())
                .map(category -> toTreeDto(category, byParentId, List.of(), List.of()))
                .toList();
    }

    @GetMapping("/{parentId}/children")
    public List<CategoryDto> listChildren(@PathVariable("parentId") Long parentId) {
        return categoryRepository.findByParentIdOrderBySortOrderAscNameAsc(parentId).stream()
                .map(this::toDto)
                .toList();
    }

    private CategoryDto toDto(Category category) {
        return new CategoryDto(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getParent() != null ? category.getParent().getId() : null,
                category.getSortOrder()
        );
    }

    private CategoryTreeDto toTreeDto(
            Category category,
            Map<Long, List<Category>> byParentId,
            List<Long> parentPathIds,
            List<String> parentPathNames
    ) {
        List<Long> pathIds = new ArrayList<>(parentPathIds);
        pathIds.add(category.getId());
        List<String> pathNames = new ArrayList<>(parentPathNames);
        pathNames.add(category.getName());

        List<CategoryTreeDto> children = byParentId.getOrDefault(category.getId(), List.of()).stream()
                .sorted(categoryComparator())
                .map(child -> toTreeDto(child, byParentId, pathIds, pathNames))
                .toList();

        return new CategoryTreeDto(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getParent() != null ? category.getParent().getId() : null,
                pathIds,
                pathNames,
                children
        );
    }

    private Comparator<Category> categoryComparator() {
        return Comparator.comparingInt(Category::getSortOrder)
                .thenComparing(Category::getName);
    }
}
