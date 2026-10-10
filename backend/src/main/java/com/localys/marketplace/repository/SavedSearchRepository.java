package com.localys.marketplace.repository;

import com.localys.marketplace.model.SavedSearch;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SavedSearchRepository extends JpaRepository<SavedSearch, Long> {

    @EntityGraph(attributePaths = "category")
    List<SavedSearch> findByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<SavedSearch> findByIdAndUser_Id(Long id, Long userId);

    long countByUser_Id(Long userId);

    @Query("select s.id from SavedSearch s where s.alertsEnabled = true order by s.id")
    List<Long> findIdsWithAlertsEnabled();
}
