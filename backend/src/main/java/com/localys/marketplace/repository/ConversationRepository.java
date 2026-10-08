package com.localys.marketplace.repository;

import com.localys.marketplace.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("""
            select c from Conversation c
            join c.participants p
            where p.user.id = :userId
            """)
    List<Conversation> findByParticipantUserId(@Param("userId") Long userId);

    /** Conversation between two users about a given listing. */
    @Query("""
            select c from Conversation c
            join c.participants p
            where p.user.id in (:userId, :otherUserId)
              and c.product.id = :productId
            group by c.id
            having count(distinct p.user.id) = 2
            """)
    List<Conversation> findBetweenUsersForProduct(
            @Param("userId") Long userId,
            @Param("otherUserId") Long otherUserId,
            @Param("productId") Long productId
    );

    /** Legacy conversation between two users that is not tied to a listing. */
    @Query("""
            select c from Conversation c
            join c.participants p
            where p.user.id in (:userId, :otherUserId)
              and c.product is null
            group by c.id
            having count(distinct p.user.id) = 2
            """)
    List<Conversation> findBetweenUsersWithoutProduct(
            @Param("userId") Long userId,
            @Param("otherUserId") Long otherUserId
    );
}
