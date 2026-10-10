package com.localys.marketplace.repository;

import com.localys.marketplace.model.ListingReport;
import com.localys.marketplace.model.enums.ReportStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ListingReportRepository extends JpaRepository<ListingReport, Long> {

    boolean existsByProduct_IdAndReporter_IdAndStatus(Long productId, Long reporterId, ReportStatus status);

    @EntityGraph(attributePaths = {"product", "reporter"})
    List<ListingReport> findByStatusOrderByCreatedAtDesc(ReportStatus status);

    @EntityGraph(attributePaths = {"product", "reporter"})
    List<ListingReport> findAllByOrderByCreatedAtDesc();

    List<ListingReport> findByProduct_IdAndStatus(Long productId, ReportStatus status);

    @Query("""
            select r.product.id as productId, count(r) as count from ListingReport r
            where r.product.id in :productIds and r.status = :status
            group by r.product.id
            """)
    List<ProductReportCount> countByProduct(
            @Param("productIds") Collection<Long> productIds,
            @Param("status") ReportStatus status
    );

    interface ProductReportCount {
        Long getProductId();

        long getCount();
    }
}
