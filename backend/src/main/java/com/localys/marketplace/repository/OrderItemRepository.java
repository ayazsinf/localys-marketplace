package com.localys.marketplace.repository;

import com.localys.marketplace.model.OrderItem;
import com.localys.marketplace.model.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

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
