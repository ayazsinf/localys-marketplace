package com.localys.marketplace.service;

import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.enums.VendorStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class ProductModerationPolicyService {

    private final boolean autoApprovalEnabled;
    private final boolean trustedVendorOnly;
    private final boolean requireImage;
    private final BigDecimal maxAutoApprovePrice;
    private final int minDescriptionLength;
    private final List<String> bannedTerms;

    public ProductModerationPolicyService(
            @Value("${app.moderation.auto-approval-enabled:false}") boolean autoApprovalEnabled,
            @Value("${app.moderation.trusted-vendor-only:true}") boolean trustedVendorOnly,
            @Value("${app.moderation.require-image:true}") boolean requireImage,
            @Value("${app.moderation.max-auto-approve-price:1000}") BigDecimal maxAutoApprovePrice,
            @Value("${app.moderation.min-description-length:20}") int minDescriptionLength,
            @Value("${app.moderation.banned-terms:whatsapp,telegram,crypto,bitcoin}") String bannedTerms
    ) {
        this.autoApprovalEnabled = autoApprovalEnabled;
        this.trustedVendorOnly = trustedVendorOnly;
        this.requireImage = requireImage;
        this.maxAutoApprovePrice = maxAutoApprovePrice;
        this.minDescriptionLength = minDescriptionLength;
        this.bannedTerms = Arrays.stream(bannedTerms.split(","))
                .map(String::trim)
                .filter(term -> !term.isBlank())
                .map(term -> term.toLowerCase(Locale.ROOT))
                .toList();
    }

    public boolean shouldAutoApprove(Product product) {
        if (!autoApprovalEnabled || product == null) {
            return false;
        }
        if (trustedVendorOnly && (product.getVendor() == null || product.getVendor().getStatus() != VendorStatus.APPROVED)) {
            return false;
        }
        if (product.getCategory() == null) {
            return false;
        }
        if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (product.getPrice().compareTo(maxAutoApprovePrice) > 0) {
            return false;
        }
        if (requireImage && (product.getImages() == null || product.getImages().isEmpty())) {
            return false;
        }
        String description = product.getDescription();
        if (description == null || description.trim().length() < minDescriptionLength) {
            return false;
        }
        return !containsBannedTerm(product.getName()) && !containsBannedTerm(description);
    }

    private boolean containsBannedTerm(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return bannedTerms.stream().anyMatch(normalized::contains);
    }
}
