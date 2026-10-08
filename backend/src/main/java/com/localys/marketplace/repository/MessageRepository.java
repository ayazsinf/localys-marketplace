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
