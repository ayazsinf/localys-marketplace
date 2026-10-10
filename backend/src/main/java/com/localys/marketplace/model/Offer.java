package com.localys.marketplace.model;

import com.localys.marketplace.model.enums.OfferStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A price negotiation between a buyer and the seller of one listing (quantity 1).
 * {@link #amount} is always the latest proposal, from either side.
 */
@Getter
@Setter
@Entity
@Table(name = "offers")
public class Offer extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private UserEntity buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private UserEntity seller;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OfferStatus status;

    /** Until when an accepted offer can be bought at its price. */
    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    /** True while the buyer can still check out at the offer price. */
    public boolean isBuyable(OffsetDateTime now) {
        return status == OfferStatus.ACCEPTED && expiresAt != null && expiresAt.isAfter(now);
    }
}
