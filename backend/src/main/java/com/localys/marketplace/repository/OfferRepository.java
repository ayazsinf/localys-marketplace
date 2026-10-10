package com.localys.marketplace.repository;

import com.localys.marketplace.model.Offer;
import com.localys.marketplace.model.enums.OfferStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Offer o where o.id = :id")
    Optional<Offer> findByIdForUpdate(@Param("id") Long id);

    boolean existsByProduct_IdAndBuyer_IdAndStatusIn(Long productId, Long buyerId, Collection<OfferStatus> statuses);

    Optional<Offer> findFirstByProduct_IdAndBuyer_IdAndStatusInOrderByCreatedAtDesc(
            Long productId, Long buyerId, Collection<OfferStatus> statuses);

    @EntityGraph(attributePaths = {"product", "seller"})
    List<Offer> findByBuyer_IdOrderByUpdatedAtDesc(Long buyerId);

    @EntityGraph(attributePaths = {"product", "buyer"})
    List<Offer> findBySeller_IdOrderByUpdatedAtDesc(Long sellerId);

    @Query("select o.id from Offer o where o.status = :status and o.expiresAt < :now")
    List<Long> findIdsByStatusExpiredBefore(@Param("status") OfferStatus status, @Param("now") OffsetDateTime now);

    @Query("""
            select o.product.id as productId, count(o) as count from Offer o
            where o.product.id in :productIds and o.status in :statuses
            group by o.product.id
            """)
    List<ProductCount> countByProduct(
            @Param("productIds") Collection<Long> productIds,
            @Param("statuses") Collection<OfferStatus> statuses
    );
}
