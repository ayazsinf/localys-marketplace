package com.localys.marketplace.repository;

import com.localys.marketplace.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("""
            select c from Conversation c
            join c.participants p
            where p.user.id = :userId
            """)
    List<Conversation> findByParticipantUserId(@Param("userId") Long userId);

    @Query("""
            select c from Conversation c
            join c.participants p
            where p.user.id in (:userId, :otherUserId)
            group by c.id
            having count(distinct p.user.id) = 2
            """)
    Optional<Conversation> findBetweenUsers(
            @Param("userId") Long userId,
            @Param("otherUserId") Long otherUserId
    );
}
