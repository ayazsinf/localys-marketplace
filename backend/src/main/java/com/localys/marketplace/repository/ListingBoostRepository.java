package com.localys.marketplace.repository;

import com.localys.marketplace.model.ListingBoost;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ListingBoostRepository extends JpaRepository<ListingBoost, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from ListingBoost b where b.providerSessionId = :sessionId")
    Optional<ListingBoost> findBySessionForUpdate(@Param("sessionId") String sessionId);
}
