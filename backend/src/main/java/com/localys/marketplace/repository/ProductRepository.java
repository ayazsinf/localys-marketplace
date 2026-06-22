package com.localys.marketplace.repository;

import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.enums.ModerationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByVendorId(Long vendorId);

    Optional<Product> findById(Long id);

    Optional<Product> findByIdAndVendorId(Long id, Long vendorId);

    Optional<Product> findByIdAndActiveTrueAndModerationStatus(Long id, ModerationStatus moderationStatus);

    boolean existsBySku(String sku);

    List<Product> findByActiveTrueAndModerationStatusOrderByNameAsc(ModerationStatus moderationStatus);

    List<Product> findByModerationStatusOrderByCreatedAtAsc(ModerationStatus moderationStatus);
}
