package com.localys.marketplace.repository;

import com.localys.marketplace.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrder_Id(Long orderId);

    Optional<Payment> findByProviderSessionId(String providerSessionId);

    Optional<Payment> findByProviderPaymentIntentId(String providerPaymentIntentId);
}
