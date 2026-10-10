package com.localys.marketplace.repository;

import com.localys.marketplace.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByConversationIdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);

    /**
     * How the user handles conversations someone else started since {@code since}: how many there
     * were, how many they answered, and their average time to the first answer.
     */
    @Query(value = """
            with started as (
                select distinct on (m.conversation_id) m.conversation_id, m.sender_id, m.created_at as asked_at
                from messages m
                join conversation_participants cp on cp.conversation_id = m.conversation_id and cp.user_id = :userId
                order by m.conversation_id, m.created_at, m.id
            )
            select count(*) as received,
                   count(reply.replied_at) as answered,
                   avg(extract(epoch from (reply.replied_at - s.asked_at))) as averageSeconds
            from started s
            left join lateral (
                select min(r.created_at) as replied_at
                from messages r
                where r.conversation_id = s.conversation_id
                  and r.sender_id = :userId
                  and r.created_at >= s.asked_at
            ) reply on true
            where s.sender_id <> :userId
              and s.asked_at >= :since
            """, nativeQuery = true)
    ResponseStats responseStats(@Param("userId") Long userId, @Param("since") OffsetDateTime since);

    interface ResponseStats {
        long getReceived();

        long getAnswered();

        Double getAverageSeconds();
    }

    Optional<Message> findTopByConversationIdOrderByCreatedAtDesc(Long conversationId);

    @Query("""
            select count(m) from Message m
            where m.conversation.id = :conversationId
              and m.sender.id <> :userId
              and m.readAt is null
            """)
    long countUnreadInConversation(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId
    );

    @Query("""
            select count(m) from Message m
            where m.sender.id <> :userId
              and m.readAt is null
              and exists (
                  select 1 from ConversationParticipant p
                  where p.conversation = m.conversation and p.user.id = :userId
              )
            """)
    long countUnreadForUser(@Param("userId") Long userId);

    @Modifying
    @Query("""
            update Message m set m.readAt = :readAt
            where m.conversation.id = :conversationId
              and m.sender.id <> :userId
              and m.readAt is null
            """)
    int markConversationRead(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("readAt") OffsetDateTime readAt
    );
}
