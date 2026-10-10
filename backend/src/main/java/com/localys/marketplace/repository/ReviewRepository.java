package com.localys.marketplace.repository;

import com.localys.marketplace.model.Review;
import com.localys.marketplace.model.enums.ReviewRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByOrderItem_IdAndReviewer_Id(Long orderItemId, Long reviewerId);

    List<Review> findByOrderItem_IdInAndReviewer_Id(Collection<Long> orderItemIds, Long reviewerId);

    @EntityGraph(attributePaths = {"reviewer", "orderItem"})
    Page<Review> findByReviewee_IdOrderByCreatedAtDesc(Long revieweeId, Pageable pageable);

    @EntityGraph(attributePaths = {"reviewer", "orderItem"})
    Page<Review> findByReviewee_IdAndRevieweeRoleOrderByCreatedAtDesc(Long revieweeId, ReviewRole role, Pageable pageable);

    @Query("""
            select r.revieweeRole as role, coalesce(avg(r.rating), 0) as average, count(r) as count
            from Review r
            where r.reviewee.id = :userId
            group by r.revieweeRole
            """)
    List<RoleSummary> summarizeByRole(@Param("userId") Long userId);

    interface RoleSummary {
        ReviewRole getRole();

        Double getAverage();

        long getCount();
    }
}
