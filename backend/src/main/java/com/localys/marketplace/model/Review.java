package com.localys.marketplace.model;

import com.localys.marketplace.model.enums.ReviewRole;
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

/**
 * One party's rating of the other after a completed sale: the buyer rates the seller and the
 * seller rates the buyer, once each per order item.
 */
@Getter
@Setter
@Entity
@Table(name = "reviews")
public class Review extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private UserEntity reviewer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewee_id", nullable = false)
    private UserEntity reviewee;

    /** Whether the reviewee was the seller or the buyer in this sale. */
    @Enumerated(EnumType.STRING)
    @Column(name = "reviewee_role", nullable = false, length = 10)
    private ReviewRole revieweeRole;

    @Column(nullable = false)
    private int rating;

    @Column(length = 1000)
    private String comment;
}
