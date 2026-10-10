package com.localys.marketplace.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@Entity
@Table(
        name = "favorites",
        uniqueConstraints = {
                @UniqueConstraint(name = "ux_favorites_user_product", columnNames = {"user_id", "product_id"})
        }
)
public class Favorite extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Price (and currency) the user last saw; a drop below it triggers a price alert. */
    @Column(name = "last_seen_price", precision = 12, scale = 2)
    private BigDecimal lastSeenPrice;

    @Column(name = "last_seen_currency", length = 3)
    private String lastSeenCurrency;

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public BigDecimal getLastSeenPrice() {
        return lastSeenPrice;
    }

    public void setLastSeenPrice(BigDecimal lastSeenPrice) {
        this.lastSeenPrice = lastSeenPrice;
    }

    public String getLastSeenCurrency() {
        return lastSeenCurrency;
    }

    public void setLastSeenCurrency(String lastSeenCurrency) {
        this.lastSeenCurrency = lastSeenCurrency;
    }
}
