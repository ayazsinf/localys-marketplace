package com.localys.marketplace.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Search filters a user saved from the home page. With alerts enabled, the alert job reports
 * listings approved after {@link #lastCheckedAt}.
 */
@Getter
@Setter
@Entity
@Table(name = "saved_searches")
public class SavedSearch extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 200)
    private String query;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "min_price", precision = 12, scale = 2)
    private BigDecimal minPrice;

    @Column(name = "max_price", precision = 12, scale = 2)
    private BigDecimal maxPrice;

    @Column(name = "alerts_enabled", nullable = false)
    private boolean alertsEnabled = true;

    @Column(name = "last_checked_at", nullable = false)
    private OffsetDateTime lastCheckedAt;
}
