package com.localys.marketplace.repository;

import com.localys.marketplace.model.OrderItem;
import com.localys.marketplace.model.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select oi from OrderItem oi where oi.id = :id")
    Optional<OrderItem> findByIdForUpdate(@Param("id") Long id);

    /** Held items the seller has not shipped although the order was paid before {@code cutoff}. */
    @Query("""
            select oi.id from OrderItem oi
            where oi.escrowStatus = com.localys.marketplace.model.enums.EscrowStatus.HELD
              and oi.shippedAt is null and oi.order.paidAt < :cutoff
            """)
    List<Long> findHeldUnshippedPaidBefore(@Param("cutoff") OffsetDateTime cutoff);

    /** Held items shipped before {@code cutoff} that the buyer neither confirmed nor disputed. */
    @Query("""
            select oi.id from OrderItem oi
            where oi.escrowStatus = com.localys.marketplace.model.enums.EscrowStatus.HELD
              and oi.shippedAt < :cutoff and oi.deliveredAt is null
            """)
    List<Long> findHeldShippedBefore(@Param("cutoff") OffsetDateTime cutoff);

    /** Items this seller sold that reached the buyer. */
    @Query("select count(oi) from OrderItem oi where oi.vendor.user.id = :sellerUserId and oi.deliveredAt is not null")
    long countDeliveredSales(@Param("sellerUserId") Long sellerUserId);

    @Query("""
            select oi from OrderItem oi
            join fetch oi.order o
            where oi.vendor.user.id = :sellerUserId
              and o.status in :statuses
            order by o.createdAt desc, oi.id asc
            """)
    List<OrderItem> findSoldItems(
            @Param("sellerUserId") Long sellerUserId,
            @Param("statuses") Collection<OrderStatus> statuses
    );
}
