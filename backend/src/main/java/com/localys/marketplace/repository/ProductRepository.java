package com.localys.marketplace.repository;

import com.localys.marketplace.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Query("""
              select distinct p from Product p
              left join fetch p.images
            """)
    List<Product> findAllWithImages();

    List<Product> findAllByOrderByNameAsc();
}
