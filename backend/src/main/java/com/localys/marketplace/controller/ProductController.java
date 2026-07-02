package com.localys.marketplace.controller;

import com.localys.marketplace.dto.ProductDetailDto;
import com.localys.marketplace.dto.ProductListDto;
import com.localys.marketplace.model.Category;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.ProductImage;
import com.localys.marketplace.model.enums.ModerationStatus;
import com.localys.marketplace.repository.ProductRepository;
import com.localys.marketplace.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductRepository repo;

    @Autowired
    private ProductService productService;

    public ProductController(ProductRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ProductListDto> list() {
        return repo.findByActiveTrueAndModerationStatusOrderByNameAsc(ModerationStatus.APPROVED).stream()
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

        Long vendorUserId = null;
        if (p.getVendor() != null && p.getVendor().getUser() != null) {
            vendorUserId = p.getVendor().getUser().getId();
        }

        return new ProductListDto(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getPrice(),
                p.getCountry(),
                p.getCurrency(),
                p.getStockQty() > 0,
                p.getCategory() != null ? p.getCategory().getId() : null,
                p.getCategory() != null ? p.getCategory().getName() : null,
                categoryPathIds(p.getCategory()),
                categoryPathNames(p.getCategory()),
                vendorUserId,
                imageUrls
        );
    }

    // Tüm ürünleri listele
    @GetMapping("/all")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ProductListDto>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts().stream()
                .map(this::toDto)
                .toList());
    }

    // Belirli bir ürünü getir
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ProductDetailDto> getProductById(@PathVariable("id") Long id) {
        Product product = productService.getProductById(id);
        return ResponseEntity.ok(toDetailDto(product));
    }

    private ProductDetailDto toDetailDto(Product product) {
        List<String> imageUrls = Optional.ofNullable(product.getImages())
                .orElseGet(List::of)
                .stream()
                .sorted(Comparator.comparingInt(ProductImage::getSortOrder))
                .map(ProductImage::getUrl)
                .toList();

        Long vendorId = product.getVendor() != null ? product.getVendor().getId() : null;
        Long vendorUserId = null;
        String vendorDisplayName = null;
        String vendorShopName = null;
        if (product.getVendor() != null) {
            vendorShopName = product.getVendor().getShopName();
            if (product.getVendor().getUser() != null) {
                vendorUserId = product.getVendor().getUser().getId();
                vendorDisplayName = product.getVendor().getUser().getDisplayName();
                if (vendorDisplayName == null || vendorDisplayName.isBlank()) {
                    vendorDisplayName = product.getVendor().getUser().getUsername();
                }
            }
        }

        return new ProductDetailDto(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCountry(),
                product.getCurrency(),
                product.getStockQty(),
                product.isActive(),
                product.getSku(),
                product.getBrand(),
                imageUrls,
                product.getCategory() != null ? product.getCategory().getId() : null,
                product.getCategory() != null ? product.getCategory().getName() : null,
                vendorId,
                vendorUserId,
                vendorDisplayName,
                vendorShopName,
                product.getCreatedAt(),
                product.getLocationText(),
                product.getLatitude(),
                product.getLongitude()
        );
    }

    private List<Long> categoryPathIds(Category category) {
        LinkedList<Long> ids = new LinkedList<>();
        Category current = category;
        while (current != null) {
            ids.addFirst(current.getId());
            current = current.getParent();
        }
        return ids;
    }

    private List<String> categoryPathNames(Category category) {
        LinkedList<String> names = new LinkedList<>();
        Category current = category;
        while (current != null) {
            names.addFirst(current.getName());
            current = current.getParent();
        }
        return names;
    }
}

