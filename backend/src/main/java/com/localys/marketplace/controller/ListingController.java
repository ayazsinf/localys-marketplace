package com.localys.marketplace.controller;

import com.localys.marketplace.dto.ListingDto;
import com.localys.marketplace.dto.RemoveListingRequest;
import com.localys.marketplace.model.Category;
import com.localys.marketplace.model.CustomUserDetails;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.ProductImage;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.Vendor;
import com.localys.marketplace.model.enums.VendorStatus;
import com.localys.marketplace.model.enums.ItemCondition;
import com.localys.marketplace.model.enums.ListingRemovalReason;
import com.localys.marketplace.repository.CategoryRepository;
import com.localys.marketplace.repository.UserRepository;
import com.localys.marketplace.repository.VendorRepository;
import com.localys.marketplace.service.ListingBoostService;
import com.localys.marketplace.service.ListingStatsService;
import com.localys.marketplace.service.ProductService;
import com.localys.marketplace.util.MarketCountryResolver;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.LinkedList;

@RestController
@RequestMapping("/api/listings")
public class ListingController {

    private final ProductService productService;
    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ListingStatsService listingStatsService;
    private final ListingBoostService listingBoostService;

    public ListingController(
            ProductService productService,
            VendorRepository vendorRepository,
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            ListingStatsService listingStatsService,
            ListingBoostService listingBoostService
    ) {
        this.listingBoostService = listingBoostService;
        this.productService = productService;
        this.vendorRepository = vendorRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.listingStatsService = listingStatsService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ListingDto> listMyListings(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Vendor vendor = vendorRepository.findByUserId(userDetails.getUserId()).orElse(null);
        if (vendor == null) {
            return List.of();
        }
        List<Product> products = productService.getVisibleProductsByVendor(vendor.getId());
        Map<Long, ListingStatsService.Stats> stats = listingStatsService.statsFor(
                products.stream().map(Product::getId).toList());
        return products.stream()
                .map(product -> toDto(product, stats.getOrDefault(product.getId(), listingStatsService.empty())))
                .toList();
    }

    /** Brings an online listing back to the top of the newest-first results (once a week). */
    /** What featuring a listing costs. */
    @GetMapping("/boost-offer")
    public ListingBoostService.BoostOffer boostOffer() {
        return listingBoostService.offer();
    }

    /** Starts the payment for featuring the listing; returns the Stripe Checkout URL. */
    @PostMapping("/{id}/boost")
    public Map<String, String> boostListing(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id
    ) {
        return Map.of("url", listingBoostService.startCheckout(userDetails.getUserId(), id));
    }

    @PostMapping("/{id}/renew")
    public ResponseEntity<ListingDto> renewListing(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id
    ) {
        Vendor vendor = getOrCreateVendor(userDetails);
        return ResponseEntity.ok(toDto(productService.renewForVendor(id, vendor)));
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

    /** Removes one photo; {@code url} is one of the listing's image URLs. */
    @DeleteMapping("/{id}/images")
    public ResponseEntity<List<String>> removeListingImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id,
            @RequestParam("url") String url
    ) {
        Vendor vendor = getOrCreateVendor(userDetails);
        return ResponseEntity.ok(productService.removeImageForVendor(id, vendor, url));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ListingDto> deleteListing(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id,
            @RequestBody(required = false) RemoveListingRequest request
    ) {
        Vendor vendor = getOrCreateVendor(userDetails);
        ListingRemovalReason reason = parseRemovalReason(request != null ? request.reason() : null);
        Product removed = productService.removeProductForVendor(id, vendor, reason, request != null ? request.note() : null);
        return ResponseEntity.ok(toDto(removed));
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
        return "Localys User";
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
        if (request.price().signum() <= 0) {
            throw new IllegalArgumentException("Listing price must be positive");
        }
        if (request.stockQty() < 0) {
            throw new IllegalArgumentException("Listing stock cannot be negative");
        }
        Product product = new Product();
        String country = MarketCountryResolver.resolveCountry(request.country(), request.currency());
        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setCountry(country);
        product.setCurrency(MarketCountryResolver.resolveCurrency(country));
        product.setStockQty(request.stockQty());
        product.setActive(request.active());
        if (request.sku() != null && !request.sku().isBlank()) {
            product.setSku(request.sku().trim());
        }
        product.setBrand(request.brand());
        product.setLocationText(request.locationText());
        product.setLatitude(request.latitude());
        product.setLongitude(request.longitude());
        if (request.condition() != null && !request.condition().isBlank()) {
            product.setCondition(ItemCondition.from(request.condition())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid item condition")));
        }
        // Older clients do not send delivery options: keep the previous default (shipping only).
        boolean handDelivery = Boolean.TRUE.equals(request.handDelivery());
        boolean shipping = request.shippingAvailable() == null || request.shippingAvailable();
        if (!handDelivery && !shipping) {
            throw new IllegalArgumentException("Choose at least one delivery option");
        }
        product.setHandDelivery(handDelivery);
        product.setShippingAvailable(shipping);
        if (request.categoryId() != null) {
            product.setCategory(categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid category")));
        }
        return product;
    }

    private ListingDto toDto(Product product) {
        return toDto(product, listingStatsService.empty());
    }

    private ListingDto toDto(Product product, ListingStatsService.Stats stats) {
        return new ListingDto(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCountry(),
                product.getCurrency(),
                product.getStockQty(),
                product.isActive(),
                product.getRemovalReason() != null ? product.getRemovalReason().name() : null,
                product.getRemovalNote(),
                product.getRemovedAt(),
                product.getExpiresAt(),
                product.getModerationStatus().name(),
                product.getModerationReason(),
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
                categoryPathIds(product.getCategory()),
                categoryPathNames(product.getCategory()),
                product.getLocationText(),
                product.getLatitude(),
                product.getLongitude(),
                product.getCondition() != null ? product.getCondition().name() : null,
                product.isHandDelivery(),
                product.isShippingAvailable(),
                product.getViewCount(),
                stats.favorites(),
                stats.conversations(),
                stats.openOffers(),
                product.getListedAt(),
                listingStatsService.renewableAt(product),
                product.getBoostedUntil()
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

    private ListingRemovalReason parseRemovalReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Removal reason is required");
        }
        try {
            return ListingRemovalReason.valueOf(reason.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid removal reason");
        }
    }
}
