package com.localys.marketplace.controller;

import com.localys.marketplace.dto.PageResponse;
import com.localys.marketplace.dto.ProductDetailDto;
import com.localys.marketplace.dto.ProductListDto;
import com.localys.marketplace.dto.UserReviewsDto;
import com.localys.marketplace.model.Category;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.ProductImage;
import com.localys.marketplace.model.enums.ItemCondition;
import com.localys.marketplace.model.CustomUserDetails;
import com.localys.marketplace.service.ListingStatsService;
import com.localys.marketplace.service.ProductSearchService;
import com.localys.marketplace.service.ReviewService;
import com.localys.marketplace.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Arrays;
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
    private final ListingStatsService listingStatsService;

    public ProductController(
            ProductService productService,
            ProductSearchService productSearchService,
            ReviewService reviewService,
            ListingStatsService listingStatsService
    ) {
        this.productService = productService;
        this.productSearchService = productSearchService;
        this.reviewService = reviewService;
        this.listingStatsService = listingStatsService;
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
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "sellerId", required = false) Long sellerId,
            @RequestParam(name = "condition", required = false) List<String> conditions,
            @RequestParam(name = "delivery", required = false) String delivery
    ) {
        List<ItemCondition> parsedConditions = conditions == null ? List.of() : conditions.stream()
                .flatMap(raw -> Arrays.stream(raw.split(",")))
                .map(ItemCondition::from)
                .flatMap(Optional::stream)
                .distinct()
                .toList();
        ProductSearchService.Criteria criteria = new ProductSearchService.Criteria(
                query, categoryId, minPrice, maxPrice, sort, page, size, sellerId,
                parsedConditions, ProductSearchService.Delivery.from(delivery)
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
                imageUrls,
                p.getCondition() != null ? p.getCondition().name() : null,
                p.isHandDelivery(),
                p.isShippingAvailable()
        );
    }

    // Belirli bir ürünü getir
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ProductDetailDto> getProductById(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal CustomUserDetails viewer
    ) {
        Product product = productService.getProductById(id);
        Long sellerId = product.getVendor() != null && product.getVendor().getUser() != null
                ? product.getVendor().getUser().getId()
                : null;
        // The seller looking at their own listing is not a view.
        if (viewer == null || !viewer.getUserId().equals(sellerId)) {
            listingStatsService.recordView(product.getId());
        }
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
        UserReviewsDto.RatingDto rating = vendorUserId == null
                ? new UserReviewsDto.RatingDto(0, 0)
                : reviewService.summary(vendorUserId);

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
                rating.count(),
                product.getCondition() != null ? product.getCondition().name() : null,
                product.isHandDelivery(),
                product.isShippingAvailable()
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

