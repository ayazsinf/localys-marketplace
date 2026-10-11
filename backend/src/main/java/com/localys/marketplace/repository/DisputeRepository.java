package com.localys.marketplace.repository;

import com.localys.marketplace.model.Dispute;
import com.localys.marketplace.model.enums.DisputeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Dispute d where d.id = :id")
    Optional<Dispute> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"orderItem", "orderItem.order", "buyer", "seller"})
    List<Dispute> findByBuyer_IdOrderByCreatedAtDesc(Long buyerId);

    @EntityGraph(attributePaths = {"orderItem", "orderItem.order", "buyer", "seller"})
    List<Dispute> findBySeller_IdOrderByCreatedAtDesc(Long sellerId);

    @EntityGraph(attributePaths = {"orderItem", "orderItem.order", "buyer", "seller"})
    List<Dispute> findByStatusInOrderByCreatedAtAsc(Collection<DisputeStatus> statuses);

    @EntityGraph(attributePaths = {"orderItem", "orderItem.order", "buyer", "seller"})
    List<Dispute> findAllByOrderByCreatedAtDesc();

    Optional<Dispute> findFirstByOrderItem_IdAndStatusIn(Long orderItemId, Collection<DisputeStatus> statuses);

    /** The latest dispute of each of these items, for showing their state on orders. */
    @Query("""
            select d from Dispute d
            where d.orderItem.id in :itemIds
              and d.createdAt = (select max(d2.createdAt) from Dispute d2 where d2.orderItem.id = d.orderItem.id)
            """)
    List<Dispute> findLatestForItems(@Param("itemIds") Collection<Long> itemIds);
}
