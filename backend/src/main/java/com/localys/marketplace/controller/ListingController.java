package com.localys.marketplace.controller;

import com.localys.marketplace.dto.ListingDto;
import com.localys.marketplace.model.CustomUserDetails;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.ProductImage;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.Vendor;
import com.localys.marketplace.model.enums.VendorStatus;
import com.localys.marketplace.repository.CategoryRepository;
import com.localys.marketplace.repository.UserRepository;
import com.localys.marketplace.repository.VendorRepository;
import com.localys.marketplace.service.ProductService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/listings")
public class ListingController {

    private final ProductService productService;
    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    public ListingController(
            ProductService productService,
            VendorRepository vendorRepository,
            UserRepository userRepository,
            CategoryRepository categoryRepository
    ) {
        this.productService = productService;
        this.vendorRepository = vendorRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public List<ListingDto> listMyListings(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Vendor vendor = vendorRepository.findByUserId(userDetails.getUserId()).orElse(null);
        if (vendor == null) {
            return List.of();
        }
        return productService.getProductsByVendor(vendor.getId()).stream()
                .map(this::toDto)
                .toList();
    }

    @PostMapping
    public ResponseEntity<ListingDto> createListing(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody ListingDto request
    ) {
        Vendor vendor = getOrCreateVendor(userDetails);
        Product created = productService.addProductForVendor(toProduct(request), vendor);
        return ResponseEntity.ok(toDto(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ListingDto> updateListing(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id,
            @RequestBody ListingDto request
    ) {
        Vendor vendor = getOrCreateVendor(userDetails);
        Product updated = productService.updateProductForVendor(id, toProduct(request), vendor);
        return ResponseEntity.ok(toDto(updated));
    }

    @PostMapping(path = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<String>> uploadListingImages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id,
            @RequestParam("images") List<MultipartFile> images
    ) {
        Vendor vendor = getOrCreateVendor(userDetails);
        List<String> urls = productService.addImagesForVendor(id, vendor, images);
        return ResponseEntity.ok(urls);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteListing(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id
    ) {
        Vendor vendor = getOrCreateVendor(userDetails);
        productService.deleteProductForVendor(id, vendor);
        return ResponseEntity.noContent().build();
    }

    private Vendor getOrCreateVendor(CustomUserDetails userDetails) {
        return vendorRepository.findByUserId(userDetails.getUserId())
                .orElseGet(() -> {
                    UserEntity user = userRepository.findById(userDetails.getUserId())
                            .orElseThrow(() -> new RuntimeException("User not found"));
                    Vendor vendor = new Vendor();
                    vendor.setUser(user);
                    vendor.setStatus(VendorStatus.APPROVED);
                    vendor.setShopName(resolveShopName(user));
                    vendor.setLegalName(vendor.getShopName());
                    return vendorRepository.save(vendor);
                });
    }

    private String resolveShopName(UserEntity user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername();
        }
        return "Localys Seller";
    }

    private Product toProduct(ListingDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Listing request is required");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Listing name is required");
        }
        if (request.price() == null) {
            throw new IllegalArgumentException("Listing price is required");
        }
        Product product = new Product();
        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setCurrency(resolveCurrency(request.currency()));
        product.setStockQty(request.stockQty());
        product.setActive(request.active());
        if (request.sku() != null && !request.sku().isBlank()) {
            product.setSku(request.sku().trim());
        }
        product.setBrand(request.brand());
        product.setLocationText(request.locationText());
        product.setLatitude(request.latitude());
        product.setLongitude(request.longitude());
        if (request.categoryId() != null) {
            product.setCategory(categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid category")));
        }
        return product;
    }

    private String resolveCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "EUR";
        }
        return currency.trim().toUpperCase();
    }

    private ListingDto toDto(Product product) {
        return new ListingDto(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCurrency(),
                product.getStockQty(),
                product.isActive(),
                product.getSku(),
                product.getBrand(),
                product.getImages().stream()
                        .map(ProductImage::getUrl)
                        .toList(),
                product.getCategory() != null ? product.getCategory().getId() : null,
                product.getCategory() != null ? product.getCategory().getName() : null,
                product.getCategory() != null && product.getCategory().getParent() != null
                        ? product.getCategory().getParent().getId()
                        : null,
                product.getLocationText(),
                product.getLatitude(),
                product.getLongitude()
        );
    }
}
