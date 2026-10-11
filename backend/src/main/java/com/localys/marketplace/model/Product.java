package com.localys.marketplace.model;

import com.localys.marketplace.model.enums.ItemCondition;
import com.localys.marketplace.model.enums.ModerationStatus;
import com.localys.marketplace.model.enums.ListingRemovalReason;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "products")
public class Product extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 80)
    private String brand;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "country_code", nullable = false, length = 2)
    private String country = "FR";

    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    @Column(name = "stock_qty", nullable = false)
    private int stockQty;

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "removal_reason", length = 40)
    private ListingRemovalReason removalReason;

    @Column(name = "removal_note", length = 1000)
    private String removalNote;

    @Column(name = "removed_at")
    private OffsetDateTime removedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 20)
    private ModerationStatus moderationStatus = ModerationStatus.PENDING;

    @Column(name = "moderation_reason", length = 1000)
    private String moderationReason;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private UserEntity reviewedBy;

    @Column(name = "location_text", length = 200)
    private String locationText;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    /** Null for listings created before conditions existed. */
    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition", length = 20)
    private ItemCondition condition;

    /** The seller agrees to meet the buyer and hand the item over. */
    @Column(name = "hand_delivery", nullable = false)
    private boolean handDelivery;

    /** The item can be bought online and shipped; hand-delivery-only items cannot be checked out. */
    @Column(name = "shipping_available", nullable = false)
    private boolean shippingAvailable = true;

    /** Detail page views by anyone but the seller; incremented atomically in the database. */
    @Column(name = "view_count", nullable = false, updatable = false, insertable = false)
    private long viewCount;

    /** Position in newest-first results: creation time, moved forward when the seller renews. */
    @Column(name = "listed_at", nullable = false)
    private OffsetDateTime listedAt;

    @PrePersist
    void initListedAt() {
        if (listedAt == null) {
            listedAt = OffsetDateTime.now();
        }
    }

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ProductImage> images = new ArrayList<>();

    /** Paid feature: shown first in newest-first results until then. */
    @Column(name = "boosted_until")
    private OffsetDateTime boostedUntil;

    public boolean isBoosted() {
        return boostedUntil != null && boostedUntil.isAfter(OffsetDateTime.now());
    }

    /** The seller turned on vacation mode. */
    public boolean isSellerAway() {
        return vendor != null && vendor.getUser() != null && vendor.getUser().isVacationMode();
    }
}
