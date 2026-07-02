package com.localys.marketplace.controller;

import com.localys.marketplace.dto.ProductListDto;
import com.localys.marketplace.model.Category;
import com.localys.marketplace.model.CustomUserDetails;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.ProductImage;
import com.localys.marketplace.service.FavoriteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ProductListDto> listFavorites(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return List.of();
        }
        return favoriteService.getFavoriteProducts(userDetails.getUserId()).stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/ids")
    public List<Long> listFavoriteIds(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return List.of();
        }
        return favoriteService.getFavoriteProductIds(userDetails.getUserId());
    }

    @PostMapping("/{productId}")
    public ResponseEntity<Void> addFavorite(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("productId") Long productId
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        favoriteService.addFavorite(userDetails.getUserId(), productId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> removeFavorite(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("productId") Long productId
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        favoriteService.removeFavorite(userDetails.getUserId(), productId);
        return ResponseEntity.noContent().build();
    }

    private ProductListDto toDto(Product product) {
        List<String> imageUrls = Optional.ofNullable(product.getImages())
                .orElseGet(List::of)
                .stream()
                .sorted(Comparator.comparingInt(ProductImage::getSortOrder))
                .map(ProductImage::getUrl)
                .toList();

        Long vendorUserId = null;
        if (product.getVendor() != null && product.getVendor().getUser() != null) {
            vendorUserId = product.getVendor().getUser().getId();
        }

        return new ProductListDto(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCountry(),
                product.getCurrency(),
                product.getStockQty() > 0,
                product.getCategory() != null ? product.getCategory().getId() : null,
                product.getCategory() != null ? product.getCategory().getName() : null,
                categoryPathIds(product.getCategory()),
                categoryPathNames(product.getCategory()),
                vendorUserId,
                imageUrls
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
