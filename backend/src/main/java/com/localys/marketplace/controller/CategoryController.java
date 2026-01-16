package com.localys.marketplace.controller;

import com.localys.marketplace.dto.CategoryDto;
import com.localys.marketplace.model.Category;
import com.localys.marketplace.repository.CategoryRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;

    public CategoryController(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public List<CategoryDto> listRootCategories() {
        return categoryRepository.findByParentIsNullOrderByNameAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/{parentId}/children")
    public List<CategoryDto> listChildren(@PathVariable("parentId") Long parentId) {
        return categoryRepository.findByParentIdOrderByNameAsc(parentId).stream()
                .map(this::toDto)
                .toList();
    }

    private CategoryDto toDto(Category category) {
        return new CategoryDto(
                category.getId(),
                category.getName(),
                category.getParent() != null ? category.getParent().getId() : null
        );
    }
}
