package com.localys.marketplace.controller;

import com.localys.marketplace.dto.PageResponse;
import com.localys.marketplace.dto.ProductDetailDto;
import com.localys.marketplace.dto.ProductListDto;
import com.localys.marketplace.model.Category;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.ProductImage;
import com.localys.marketplace.service.ProductSearchService;
import com.localys.marketplace.service.ReviewService;
import com.localys.marketplace.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;
    private final ProductSearchService productSearchService;
    private final ReviewService reviewService;

    public ProductController(
            ProductService productService,
            ProductSearchService productSearchService,
            ReviewService reviewService
    ) {
        this.productService = productService;
        this.productSearchService = productSearchService;
        this.reviewService = reviewService;
    }

    /**
     * Public listing search. {@code sort} is one of NEWEST (default), PRICE_ASC, PRICE_DESC, NAME_ASC.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<ProductListDto> search(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(name = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        ProductSearchService.Criteria criteria = new ProductSearchService.Criteria(
                query, categoryId, minPrice, maxPrice, sort, page, size
        );
        return PageResponse.from(productSearchService.search(criteria), this::toDto);
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
        ReviewService.Summary rating = vendorId == null
                ? new ReviewService.Summary(0, 0)
                : reviewService.summary(vendorId);

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
                product.getLongitude(),
                rating.average(),
                rating.count()
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

