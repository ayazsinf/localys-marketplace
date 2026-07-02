package com.localys.marketplace.controller;

import com.localys.marketplace.dto.AdminListingDto;
import com.localys.marketplace.model.CustomUserDetails;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.ProductImage;
import com.localys.marketplace.model.enums.ModerationStatus;
import com.localys.marketplace.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/listings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminListingController {

    private final ProductService productService;

    public AdminListingController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AdminListingDto> list(
            @RequestParam(name = "status", defaultValue = "PENDING") ModerationStatus status
    ) {
        return productService.getProductsForModeration(status).stream()
                .map(this::toDto)
                .toList();
    }

    @PostMapping("/{id}/approve")
    @Transactional
    public ResponseEntity<AdminListingDto> approve(
            @AuthenticationPrincipal CustomUserDetails reviewer,
            @PathVariable("id") Long id
    ) {
        return ResponseEntity.ok(toDto(productService.approveProduct(id, reviewer.getUser())));
    }

    @PostMapping("/{id}/reject")
    @Transactional
    public ResponseEntity<AdminListingDto> reject(
            @AuthenticationPrincipal CustomUserDetails reviewer,
            @PathVariable("id") Long id,
            @RequestBody RejectListingRequest request
    ) {
        return ResponseEntity.ok(toDto(productService.rejectProduct(id, request.reason(), reviewer.getUser())));
    }

    private AdminListingDto toDto(Product product) {
        List<String> imageUrls = Optional.ofNullable(product.getImages())
                .orElseGet(List::of)
                .stream()
                .sorted(Comparator.comparingInt(ProductImage::getSortOrder))
                .map(ProductImage::getUrl)
                .toList();
        String vendorDisplayName = null;
        Long vendorUserId = null;
        if (product.getVendor() != null && product.getVendor().getUser() != null) {
            vendorUserId = product.getVendor().getUser().getId();
            vendorDisplayName = product.getVendor().getUser().getDisplayName();
            if (vendorDisplayName == null || vendorDisplayName.isBlank()) {
                vendorDisplayName = product.getVendor().getUser().getUsername();
            }
        }
        return new AdminListingDto(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCountry(),
                product.getCurrency(),
                product.getStockQty(),
                product.isActive(),
                product.getModerationStatus().name(),
                product.getModerationReason(),
                product.getSku(),
                product.getBrand(),
                imageUrls,
                product.getCategory() != null ? product.getCategory().getId() : null,
                product.getCategory() != null ? product.getCategory().getName() : null,
                product.getVendor() != null ? product.getVendor().getId() : null,
                vendorUserId,
                vendorDisplayName,
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getReviewedAt()
        );
    }

    public record RejectListingRequest(String reason) {
    }
}
