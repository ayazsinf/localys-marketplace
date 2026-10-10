package com.localys.marketplace.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "cart_items",
        uniqueConstraints = @UniqueConstraint(name = "ux_cart_item_unique_product", columnNames = {"cart_id", "product_id"})
)
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPriceSnapshot;

    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    /** Accepted offer this line is bought with; null to pay the listing price. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_id")
    private Offer offer;

    /**
     * What the buyer pays per unit right now: the accepted offer while it is still valid,
     * otherwise the listing's current price (never a stale price from when it was added).
     */
    public BigDecimal currentUnitPrice(OffsetDateTime now) {
        if (offer != null && offer.isBuyable(now)) {
            return offer.getAmount();
        }
        return product.getPrice();
    }

    public boolean hasValidOffer(OffsetDateTime now) {
        return offer != null && offer.isBuyable(now);
    }
}
