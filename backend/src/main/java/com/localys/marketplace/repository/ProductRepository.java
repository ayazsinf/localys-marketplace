package com.localys.marketplace.repository;

import com.localys.marketplace.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByVendorId(Long vendorId);

    Optional<Product> findById(Long id);

    Optional<Product> findByIdAndVendorId(Long id, Long vendorId);

    boolean existsBySku(String sku);

    // Ürünleri ada göre artan sırayla listele
    List<Product> findAllByOrderByNameAsc();
}
