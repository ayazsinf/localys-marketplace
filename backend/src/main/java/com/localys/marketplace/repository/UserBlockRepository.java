package com.localys.marketplace.repository;

import com.localys.marketplace.model.UserBlock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    boolean existsByBlocker_IdAndBlocked_Id(Long blockerId, Long blockedId);

    Optional<UserBlock> findByBlocker_IdAndBlocked_Id(Long blockerId, Long blockedId);

    @EntityGraph(attributePaths = "blocked")
    List<UserBlock> findByBlocker_IdOrderByCreatedAtDesc(Long blockerId);

    @Query("""
            select count(b) > 0 from UserBlock b
            where (b.blocker.id = :a and b.blocked.id = :b) or (b.blocker.id = :b and b.blocked.id = :a)
            """)
    boolean existsBetween(@Param("a") Long a, @Param("b") Long b);
}
