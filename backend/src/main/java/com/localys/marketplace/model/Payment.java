package com.localys.marketplace.model;

import com.localys.marketplace.model.enums.PaymentProvider;
import com.localys.marketplace.model.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "ux_payments_order", columnNames = {"order_id"}),
                @UniqueConstraint(name = "ux_payments_idempotency", columnNames = {"idempotency_key"})
        }
)
public class Payment extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider provider = PaymentProvider.STRIPE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.REQUIRES_PAYMENT;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    @Column(name = "provider_session_id", length = 200)
    private String providerSessionId;

    @Column(name = "provider_payment_intent_id", length = 200)
    private String providerPaymentIntentId;

    @Column(name = "idempotency_key", nullable = false, length = 80)
    private String idempotencyKey;
}
