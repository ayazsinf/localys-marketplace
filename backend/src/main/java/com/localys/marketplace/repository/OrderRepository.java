package com.localys.marketplace.repository;

import com.localys.marketplace.model.Order;
import com.localys.marketplace.model.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    List<Order> findByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<Order> findByIdAndUser_Id(Long id, Long userId);

    @Query("select o.id from Order o where o.status = :status and o.createdAt < :before")
    List<Long> findIdsByStatusCreatedBefore(
            @Param("status") OrderStatus status,
            @Param("before") OffsetDateTime before
    );
}
