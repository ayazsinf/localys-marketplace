package com.localys.marketplace.repository;

import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.enums.ModerationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

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

    // Ordered by id so concurrent checkouts always lock rows in the same order (no deadlocks).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    boolean existsBySku(String sku);

    List<Product> findByModerationStatusOrderByCreatedAtAsc(ModerationStatus moderationStatus);

    @Query("""
            select p from Product p
            where p.active = true
              and p.removedAt is null
              and p.expiresAt <= :now
            order by p.expiresAt asc
            """)
    List<Product> findExpiredActiveListings(@Param("now") OffsetDateTime now, Pageable pageable);

    @Modifying
    @Query("update Product p set p.viewCount = p.viewCount + 1 where p.id = :id")
    int incrementViewCount(@Param("id") Long id);
}
