package com.localys.marketplace.service;

import com.localys.marketplace.event.ProductModerationEvent;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.ProductImage;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.Vendor;
import com.localys.marketplace.model.enums.ModerationStatus;
import com.localys.marketplace.model.enums.ProductModerationEventType;
import com.localys.marketplace.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private MediaStorageService mediaStorageService;

    @Autowired
    private ProductModerationPolicyService moderationPolicyService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public List<Product> getProductsByVendor(Long vendorId) {
        return productRepository.findByVendorId(vendorId);
    }

    public List<Product> getAllProducts() {
        return productRepository.findByActiveTrueAndModerationStatusOrderByNameAsc(ModerationStatus.APPROVED);
    }

    public Product getProductById(Long id) {
        return productRepository.findByIdAndActiveTrueAndModerationStatus(id, ModerationStatus.APPROVED)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    public List<Product> getProductsForModeration(ModerationStatus status) {
        return productRepository.findByModerationStatusOrderByCreatedAtAsc(status);
    }

    public Product addProductForVendor(Product product, Vendor vendor) {
        product.setVendor(vendor);
        if (product.getSku() == null || product.getSku().isBlank()) {
            product.setSku(generateSku());
        }
        ProductModerationEventType eventType = applyInitialModeration(product);
        Product saved = productRepository.save(product);
        publishModerationEvent(saved, eventType);
        return saved;
    }

    public Product updateProductForVendor(Long id, Product productDetails, Vendor vendor) {
        Product product = productRepository.findByIdAndVendorId(id, vendor.getId())
                .orElseThrow(() -> new RuntimeException("Product not found"));
        boolean moderatedContentChanged = hasModeratedContentChanged(product, productDetails);
        product.setName(productDetails.getName());
        product.setPrice(productDetails.getPrice());
        product.setDescription(productDetails.getDescription());
        product.setBrand(productDetails.getBrand());
        product.setStockQty(productDetails.getStockQty());
        product.setActive(productDetails.isActive());
        product.setCountry(productDetails.getCountry());
        product.setCurrency(productDetails.getCurrency());
        if (productDetails.getSku() != null && !productDetails.getSku().isBlank()) {
            product.setSku(productDetails.getSku());
        }
        product.setCategory(productDetails.getCategory());
        product.setLocationText(productDetails.getLocationText());
        product.setLatitude(productDetails.getLatitude());
        product.setLongitude(productDetails.getLongitude());
        ProductModerationEventType eventType = null;
        if (moderatedContentChanged) {
            eventType = applyModerationPolicy(product);
        }
        Product saved = productRepository.save(product);
        publishModerationEvent(saved, eventType);
        return saved;
    }

    public void deleteProductForVendor(Long id, Vendor vendor) {
        Product product = productRepository.findByIdAndVendorId(id, vendor.getId())
                .orElseThrow(() -> new RuntimeException("Product not found"));
        productRepository.delete(product);
    }

    public List<String> addImagesForVendor(Long id, Vendor vendor, List<MultipartFile> files) {
        Product product = productRepository.findByIdAndVendorId(id, vendor.getId())
                .orElseThrow(() -> new RuntimeException("Product not found"));
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

    public Product approveProduct(Long id, UserEntity reviewer) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        product.setModerationStatus(ModerationStatus.APPROVED);
        product.setModerationReason(null);
        product.setReviewedAt(OffsetDateTime.now());
        product.setReviewedBy(reviewer);
        Product saved = productRepository.save(product);
        publishModerationEvent(saved, ProductModerationEventType.APPROVED);
        return saved;
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
                || !Objects.equals(current.getLongitude(), updated.getLongitude());
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
