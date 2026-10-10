package com.localys.marketplace.service;

import com.localys.marketplace.event.ProductModerationEvent;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.ProductImage;
import com.localys.marketplace.model.ProductRemovalEvent;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.Vendor;
import com.localys.marketplace.model.enums.ModerationStatus;
import com.localys.marketplace.model.enums.ProductModerationEventType;
import com.localys.marketplace.model.enums.ListingRemovalReason;
import com.localys.marketplace.repository.ProductRepository;
import com.localys.marketplace.repository.ProductRemovalEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.UUID;

@Service
public class ProductService {

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);

    // Sold or admin-removed listings must stay closed; the seller has to create a new listing.
    private static final Set<ListingRemovalReason> NON_REACTIVATABLE_REASONS =
            EnumSet.of(ListingRemovalReason.SOLD_ON_LOCALYS, ListingRemovalReason.ADMIN_REMOVED);

    static final int MAX_IMAGES_PER_LISTING = 10;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private MediaStorageService mediaStorageService;

    @Autowired
    private ProductModerationPolicyService moderationPolicyService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ProductRemovalEventRepository productRemovalEventRepository;

    @Autowired
    private PriceDropService priceDropService;

    @Value("${app.listings.removed-visible-days:7}")
    private int removedVisibleDays;

    @Value("${app.listings.max-active-days:45}")
    private int maxActiveDays;

    public List<Product> getVisibleProductsByVendor(Long vendorId) {
        int days = Math.max(1, removedVisibleDays);
        return productRepository.findVisibleForVendor(vendorId, OffsetDateTime.now().minusDays(days));
    }

    public Product getProductById(Long id) {
        return productRepository.findPublicById(id, ModerationStatus.APPROVED, OffsetDateTime.now())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
    }

    public List<Product> getProductsForModeration(ModerationStatus status) {
        return productRepository.findByModerationStatusOrderByCreatedAtAsc(status);
    }

    public List<Product> getAllProductsForAdmin() {
        return productRepository.findAllByOrderByCreatedAtDesc();
    }

    public Product addProductForVendor(Product product, Vendor vendor) {
        product.setVendor(vendor);
        if (product.getSku() == null || product.getSku().isBlank()) {
            product.setSku(generateSku());
        }
        product.setExpiresAt(resolveExpiry());
        ProductModerationEventType eventType = applyInitialModeration(product);
        Product saved = productRepository.save(product);
        publishModerationEvent(saved, eventType);
        return saved;
    }

    public Product updateProductForVendor(Long id, Product productDetails, Vendor vendor) {
        Product product = productRepository.findByIdAndVendorId(id, vendor.getId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        if (productDetails.isActive() && !product.isActive()
                && NON_REACTIVATABLE_REASONS.contains(product.getRemovalReason())) {
            throw new IllegalArgumentException("Listing cannot be reactivated");
        }
        boolean moderatedContentChanged = hasModeratedContentChanged(product, productDetails);
        product.setName(productDetails.getName());
        product.setPrice(productDetails.getPrice());
        product.setDescription(productDetails.getDescription());
        product.setBrand(productDetails.getBrand());
        product.setStockQty(productDetails.getStockQty());
        product.setActive(productDetails.isActive());
        if (productDetails.isActive()) {
            product.setRemovalReason(null);
            product.setRemovalNote(null);
            product.setRemovedAt(null);
            if (product.getExpiresAt() == null || !product.getExpiresAt().isAfter(OffsetDateTime.now())) {
                product.setExpiresAt(resolveExpiry());
            }
        }
        product.setCountry(productDetails.getCountry());
        product.setCurrency(productDetails.getCurrency());
        if (productDetails.getSku() != null && !productDetails.getSku().isBlank()) {
            product.setSku(productDetails.getSku());
        }
        product.setCategory(productDetails.getCategory());
        product.setLocationText(productDetails.getLocationText());
        product.setLatitude(productDetails.getLatitude());
        product.setLongitude(productDetails.getLongitude());
        product.setCondition(productDetails.getCondition());
        product.setHandDelivery(productDetails.isHandDelivery());
        product.setShippingAvailable(productDetails.isShippingAvailable());
        ProductModerationEventType eventType = null;
        if (moderatedContentChanged) {
            eventType = applyModerationPolicy(product);
        }
        Product saved = productRepository.save(product);
        publishModerationEvent(saved, eventType);
        notifyPriceDrop(saved);
        return saved;
    }

    public Product expireProduct(Product product) {
        return markRemoved(product, ListingRemovalReason.EXPIRED, null, null);
    }

    public Product removeProductForVendor(Long id, Vendor vendor, ListingRemovalReason reason, String note) {
        Product product = productRepository.findByIdAndVendorId(id, vendor.getId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        return markRemoved(product, reason, note, vendor.getUser());
    }

    public Product removeProductByAdmin(Long id, String note, UserEntity actor) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        return markRemoved(product, ListingRemovalReason.ADMIN_REMOVED, note, actor);
    }

    public List<String> addImagesForVendor(Long id, Vendor vendor, List<MultipartFile> files) {
        Product product = productRepository.findByIdAndVendorId(id, vendor.getId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        long incoming = files == null ? 0 : files.stream().filter(file -> file != null && !file.isEmpty()).count();
        if (product.getImages().size() + incoming > MAX_IMAGES_PER_LISTING) {
            throw new IllegalArgumentException("Too many images for this listing");
        }
        List<String> urls = mediaStorageService.storeListingImages(product.getId(), files);
        if (urls.isEmpty()) {
            return urls;
        }
        int sortOrderStart = product.getImages().size();
        for (int i = 0; i < urls.size(); i++) {
            ProductImage image = new ProductImage();
            image.setProduct(product);
            image.setUrl(urls.get(i));
            image.setSortOrder(sortOrderStart + i);
            product.getImages().add(image);
        }
        ProductModerationEventType eventType = applyModerationPolicy(product);
        Product saved = productRepository.save(product);
        publishModerationEvent(saved, eventType);
        return urls;
    }

    /**
     * Removes one photo of the seller's listing and returns the remaining image URLs.
     * The stored file is kept; it is no longer referenced by the listing.
     */
    @Transactional
    public List<String> removeImageForVendor(Long id, Vendor vendor, String url) {
        Product product = productRepository.findByIdAndVendorId(id, vendor.getId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        boolean removed = product.getImages().removeIf(image -> image.getUrl().equals(url));
        if (!removed) {
            throw new IllegalArgumentException("Image not found");
        }
        List<ProductImage> remaining = product.getImages().stream()
                .sorted(Comparator.comparingInt(ProductImage::getSortOrder))
                .toList();
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setSortOrder(i);
        }
        productRepository.save(product);
        return remaining.stream().map(ProductImage::getUrl).toList();
    }

    /**
     * Moves an online listing back to the top of the newest-first results. Allowed once a week
     * so sellers cannot keep their listings permanently on top.
     */
    @Transactional
    public Product renewForVendor(Long id, Vendor vendor) {
        Product product = productRepository.findByIdAndVendorId(id, vendor.getId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        OffsetDateTime now = OffsetDateTime.now();
        if (!product.isActive() || product.getRemovedAt() != null
                || product.getModerationStatus() != ModerationStatus.APPROVED
                || product.getExpiresAt() == null || !product.getExpiresAt().isAfter(now)) {
            throw new IllegalArgumentException("Only online listings can be renewed");
        }
        if (product.getListedAt().plus(ListingStatsService.RENEW_INTERVAL).isAfter(now)) {
            throw new IllegalArgumentException("Listing was renewed recently");
        }
        product.setListedAt(now);
        return productRepository.save(product);
    }

    public Product approveProduct(Long id, UserEntity reviewer) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        product.setModerationStatus(ModerationStatus.APPROVED);
        product.setModerationReason(null);
        product.setReviewedAt(OffsetDateTime.now());
        product.setReviewedBy(reviewer);
        Product saved = productRepository.save(product);
        publishModerationEvent(saved, ProductModerationEventType.APPROVED);
        notifyPriceDrop(saved);
        return saved;
    }

    /** A failed alert must never fail the seller's edit or the admin's approval. */
    private void notifyPriceDrop(Product product) {
        try {
            priceDropService.notifyIfPriceDropped(product.getId());
        } catch (RuntimeException ex) {
            logger.warn("Could not send price drop alerts for product {}: {}", product.getId(), ex.getMessage());
        }
    }

    public Product rejectProduct(Long id, String reason, UserEntity reviewer) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        if (reason.trim().length() > 1000) {
            throw new IllegalArgumentException("Rejection reason is too long");
        }
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        product.setModerationStatus(ModerationStatus.REJECTED);
        product.setModerationReason(reason.trim());
        product.setReviewedAt(OffsetDateTime.now());
        product.setReviewedBy(reviewer);
        Product saved = productRepository.save(product);
        publishModerationEvent(saved, ProductModerationEventType.REJECTED);
        return saved;
    }

    private ProductModerationEventType applyInitialModeration(Product product) {
        if (moderationPolicyService.shouldAutoApprove(product)) {
            markAutoApproved(product);
            return ProductModerationEventType.APPROVED;
        }
        markPending(product);
        return ProductModerationEventType.SUBMITTED;
    }

    private ProductModerationEventType applyModerationPolicy(Product product) {
        ModerationStatus previousStatus = product.getModerationStatus();
        if (moderationPolicyService.shouldAutoApprove(product)) {
            markAutoApproved(product);
            return previousStatus == ModerationStatus.APPROVED ? null : ProductModerationEventType.APPROVED;
        }
        boolean becamePending = previousStatus != ModerationStatus.PENDING;
        markPending(product);
        return becamePending ? ProductModerationEventType.SUBMITTED : null;
    }

    private void markPending(Product product) {
        product.setModerationStatus(ModerationStatus.PENDING);
        product.setModerationReason(null);
        product.setReviewedAt(null);
        product.setReviewedBy(null);
    }

    private void markAutoApproved(Product product) {
        product.setModerationStatus(ModerationStatus.APPROVED);
        product.setModerationReason(null);
        product.setReviewedAt(OffsetDateTime.now());
        product.setReviewedBy(null);
    }

    private Product markRemoved(Product product, ListingRemovalReason reason, String note, UserEntity actor) {
        if (reason == null) {
            throw new IllegalArgumentException("Removal reason is required");
        }
        String normalizedNote = note == null || note.isBlank() ? null : note.trim();
        if (normalizedNote != null && normalizedNote.length() > 1000) {
            throw new IllegalArgumentException("Removal note is too long");
        }
        product.setActive(false);
        product.setRemovalReason(reason);
        product.setRemovalNote(normalizedNote);
        product.setRemovedAt(OffsetDateTime.now());
        Product saved = productRepository.save(product);
        ProductRemovalEvent event = new ProductRemovalEvent();
        event.setProduct(saved);
        event.setReason(reason);
        event.setNote(normalizedNote);
        event.setActorUser(actor);
        event.setOccurredAt(saved.getRemovedAt());
        productRemovalEventRepository.save(event);
        return saved;
    }

    private OffsetDateTime resolveExpiry() {
        return OffsetDateTime.now().plusDays(Math.max(1, maxActiveDays));
    }

    private void publishModerationEvent(Product product, ProductModerationEventType eventType) {
        if (product == null || product.getId() == null || eventType == null) {
            return;
        }
        eventPublisher.publishEvent(new ProductModerationEvent(product.getId(), eventType));
    }

    private boolean hasModeratedContentChanged(Product current, Product updated) {
        Long currentCategoryId = current.getCategory() != null ? current.getCategory().getId() : null;
        Long updatedCategoryId = updated.getCategory() != null ? updated.getCategory().getId() : null;
        String updatedSku = updated.getSku() == null || updated.getSku().isBlank()
                ? current.getSku()
                : updated.getSku();
        return !Objects.equals(current.getName(), updated.getName())
                || !Objects.equals(current.getDescription(), updated.getDescription())
                || !Objects.equals(current.getPrice(), updated.getPrice())
                || !Objects.equals(current.getCountry(), updated.getCountry())
                || !Objects.equals(current.getCurrency(), updated.getCurrency())
                || !Objects.equals(current.getSku(), updatedSku)
                || !Objects.equals(current.getBrand(), updated.getBrand())
                || !Objects.equals(currentCategoryId, updatedCategoryId)
                || !Objects.equals(current.getLocationText(), updated.getLocationText())
                || !Objects.equals(current.getLatitude(), updated.getLatitude())
                || !Objects.equals(current.getLongitude(), updated.getLongitude())
                || current.getCondition() != updated.getCondition();
    }

    private String generateSku() {
        String sku;
        int attempts = 0;
        do {
            sku = "LCY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
            attempts++;
        } while (productRepository.existsBySku(sku) && attempts < 5);
        return sku;
    }
}
