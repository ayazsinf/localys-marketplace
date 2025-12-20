package com.localys.marketplace.controller;

import com.localys.marketplace.dto.ProductListDto;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.ProductImage;
import com.localys.marketplace.repository.ProductRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductRepository repo;

    public ProductController(ProductRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<ProductListDto> list() {
        return repo.findAllByOrderByNameAsc().stream()
                .map(this::toDto)
                .toList();
    }

    private ProductListDto toDto(Product p) {
        List<String> imageUrls = Optional.ofNullable(p.getImages())
                .orElseGet(List::of)
                .stream()
                .sorted(Comparator.comparingInt(ProductImage::getSortOrder))
                .map(ProductImage::getUrl)
                .toList();

        return new ProductListDto(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getPrice(),
                p.getCurrency(),                               // String currency
                p.getStockQty() > 0,                                 // boolean inStock (veya getInStock())
                p.getCategory() != null ? p.getCategory().getName() : null,  // String categoryName
                imageUrls
        );
    }
}

