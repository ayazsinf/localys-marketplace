package com.localys.marketplace.model;

import com.localys.marketplace.model.enums.EscrowStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(name = "product_name_snapshot", nullable = false, length = 200)
    private String productNameSnapshot;

    @Column(name = "unit_price_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPriceSnapshot;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Column(name = "shipped_at")
    private OffsetDateTime shippedAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    /** Buyer protection state; null until the order is paid. */
    @Enumerated(EnumType.STRING)
    @Column(name = "escrow_status", length = 20)
    private EscrowStatus escrowStatus;

    @Column(name = "escrow_released_at")
    private OffsetDateTime escrowReleasedAt;

    @Column(name = "refunded_at")
    private OffsetDateTime refundedAt;

    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount;

    /** What the platform keeps, fixed when the money is released. */
    @Column(name = "commission_amount", precision = 12, scale = 2)
    private BigDecimal commissionAmount;

    /** What the seller receives: the line total minus the commission. */
    @Column(name = "payout_amount", precision = 12, scale = 2)
    private BigDecimal payoutAmount;

    /** The transfer to the seller's account; null until paid out. */
    @Column(name = "transfer_id", length = 100)
    private String transferId;

    @Column(name = "paid_out_at")
    private OffsetDateTime paidOutAt;

    /** The payment provider's refund id. */
    @Column(name = "refund_id", length = 100)
    private String refundId;

    /** The accepted offer that set this line's price, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_id")
    private Offer offer;
}
