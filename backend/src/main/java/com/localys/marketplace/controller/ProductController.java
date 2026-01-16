package com.localys.marketplace.controller;

import com.localys.marketplace.dto.ProductDetailDto;
import com.localys.marketplace.dto.ProductListDto;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.ProductImage;
import com.localys.marketplace.repository.ProductRepository;
import com.localys.marketplace.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
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

    // Tüm ürünleri listele
    @GetMapping("/all")
    public ResponseEntity<List<Product>> getAllProducts() {
        List<Product> products = productService.getAllProducts();
        return ResponseEntity.ok(products);
    }

    // Belirli bir ürünü getir
    @GetMapping("/{id}")
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
}

