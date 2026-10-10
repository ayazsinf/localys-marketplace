package com.localys.marketplace.repository;

import com.localys.marketplace.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByOrderItem_Id(Long orderItemId);

    List<Review> findByOrderItem_IdIn(Collection<Long> orderItemIds);

    @EntityGraph(attributePaths = {"reviewer", "orderItem"})
    Page<Review> findByVendor_IdOrderByCreatedAtDesc(Long vendorId, Pageable pageable);

    @Query("select coalesce(avg(r.rating), 0) as average, count(r) as count from Review r where r.vendor.id = :vendorId")
    RatingSummary summarize(@Param("vendorId") Long vendorId);

    interface RatingSummary {
        Double getAverage();

        long getCount();
    }
}
