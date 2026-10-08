package com.localys.marketplace.config;

import com.localys.marketplace.model.CustomUserDetails;
import com.localys.marketplace.repository.ConversationParticipantRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.security.Principal;

/**
 * Authorizes STOMP subscriptions. Without this, any logged-in user could subscribe to
 * {@code /topic/conversations/{id}} of a conversation they are not part of and read it live.
 */
public class StompSubscriptionInterceptor implements ChannelInterceptor {

    private static final String CONVERSATION_TOPIC_PREFIX = "/topic/conversations/";

    private final ConversationParticipantRepository participantRepository;

    public StompSubscriptionInterceptor(ConversationParticipantRepository participantRepository) {
        this.participantRepository = participantRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith("/topic/")) {
            // /user/** destinations are already scoped to the session's principal.
            return message;
        }
        if (!destination.startsWith(CONVERSATION_TOPIC_PREFIX)) {
            throw new AccessDeniedException("Subscription not allowed");
        }
        Long conversationId = parseId(destination.substring(CONVERSATION_TOPIC_PREFIX.length()));
        Long userId = resolveUserId(accessor.getUser());
        if (conversationId == null || userId == null
                || !participantRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new AccessDeniedException("Subscription not allowed");
        }
        return message;
    }

    private Long resolveUserId(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof CustomUserDetails details) {
            return details.getUserId();
        }
        return null;
    }

    private Long parseId(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
