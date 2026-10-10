package com.localys.marketplace.repository;

import com.localys.marketplace.model.Favorite;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByUserId(Long userId);

    @EntityGraph(attributePaths = "user")
    List<Favorite> findByProductId(Long productId);

    Optional<Favorite> findByUserIdAndProductId(Long userId, Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    void deleteByUserIdAndProductId(Long userId, Long productId);

    @Query("""
            select f.product.id as productId, count(f) as count from Favorite f
            where f.product.id in :productIds
            group by f.product.id
            """)
    List<ProductCount> countByProduct(@Param("productIds") Collection<Long> productIds);
}
