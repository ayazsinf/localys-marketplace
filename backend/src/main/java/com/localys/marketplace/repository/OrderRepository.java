package com.localys.marketplace.repository;

import com.localys.marketplace.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
