package com.localys.marketplace.repository;

import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.enums.ModerationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByVendorId(Long vendorId);

    @Query("""
            select p from Product p
            where p.vendor.id = :vendorId
              and (p.removedAt is null or p.removedAt >= :removedAfter)
            order by p.createdAt desc
            """)
    List<Product> findVisibleForVendor(
            @Param("vendorId") Long vendorId,
            @Param("removedAfter") OffsetDateTime removedAfter
    );

    List<Product> findAllByOrderByCreatedAtDesc();

    Optional<Product> findById(Long id);

    Optional<Product> findByIdAndVendorId(Long id, Long vendorId);

    @Query("""
            select p from Product p
            where p.id = :id
              and p.active = true
              and p.removedAt is null
              and p.expiresAt > :now
              and p.moderationStatus = :moderationStatus
            """)
    Optional<Product> findPublicById(
            @Param("id") Long id,
            @Param("moderationStatus") ModerationStatus moderationStatus,
            @Param("now") OffsetDateTime now
    );

    boolean existsBySku(String sku);

    @Query("""
            select p from Product p
            where p.active = true
              and p.removedAt is null
              and p.expiresAt > :now
              and p.moderationStatus = :moderationStatus
            order by p.name asc
            """)
    List<Product> findPublicListings(
            @Param("moderationStatus") ModerationStatus moderationStatus,
            @Param("now") OffsetDateTime now
    );

    List<Product> findByModerationStatusOrderByCreatedAtAsc(ModerationStatus moderationStatus);

    @Query("""
            select p from Product p
            where p.active = true
              and p.removedAt is null
              and p.expiresAt <= :now
            order by p.expiresAt asc
            """)
    List<Product> findExpiredActiveListings(@Param("now") OffsetDateTime now, Pageable pageable);
}
