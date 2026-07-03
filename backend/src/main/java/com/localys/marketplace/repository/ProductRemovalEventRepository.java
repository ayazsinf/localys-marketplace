package com.localys.marketplace.repository;

import com.localys.marketplace.model.ProductRemovalEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRemovalEventRepository extends JpaRepository<ProductRemovalEvent, Long> {
}
