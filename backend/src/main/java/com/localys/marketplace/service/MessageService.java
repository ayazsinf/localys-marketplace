package com.localys.marketplace.service;

import com.localys.marketplace.dto.ConversationDto;
import com.localys.marketplace.dto.MessageDto;
import com.localys.marketplace.model.Conversation;
import com.localys.marketplace.model.ConversationParticipant;
import com.localys.marketplace.model.Message;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.repository.ConversationParticipantRepository;
import com.localys.marketplace.repository.ConversationRepository;
import com.localys.marketplace.repository.MessageRepository;
import com.localys.marketplace.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class MessageService {

    private static final int DEFAULT_PAGE_SIZE = 50;

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public MessageService(
            ConversationRepository conversationRepository,
            ConversationParticipantRepository participantRepository,
            MessageRepository messageRepository,
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public List<ConversationDto> listConversations(Long userId) {
        return conversationRepository.findByParticipantUserId(userId).stream()
                .map(conversation -> toConversationDto(conversation, userId))
                .sorted(Comparator.comparing(ConversationDto::lastMessageAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public ConversationDto getOrCreateConversation(Long userId, Long otherUserId) {
        Conversation conversation = conversationRepository.findBetweenUsers(userId, otherUserId)
                .orElseGet(() -> createConversation(userId, otherUserId));
        return toConversationDto(conversation, userId);
    }

    public List<MessageDto> getMessages(Long userId, Long conversationId, Integer limit) {
        ensureParticipant(conversationId, userId);
        int pageSize = limit != null && limit > 0 ? limit : DEFAULT_PAGE_SIZE;
        List<Message> messages = messageRepository
                .findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, pageSize))
                .getContent();
        return messages.stream()
                .sorted(Comparator.comparing(Message::getCreatedAt))
                .map(this::toMessageDto)
                .toList();
    }

    @Transactional
    public MessageDto sendMessage(Long senderId, Long conversationId, Long recipientId, String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Message body cannot be empty.");
        }

        Conversation conversation = resolveConversation(senderId, conversationId, recipientId);
        ensureParticipant(conversation.getId(), senderId);

        UserEntity sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setBody(body.trim());

        Message saved = messageRepository.save(message);
        conversation.setLastMessageAt(saved.getCreatedAt());
        conversationRepository.save(conversation);

        MessageDto dto = toMessageDto(saved);
        messagingTemplate.convertAndSend("/topic/conversations/" + conversation.getId(), dto);
        return dto;
    }

    private Conversation resolveConversation(Long senderId, Long conversationId, Long recipientId) {
        if (conversationId != null) {
            return conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new RuntimeException("Conversation not found"));
        }
        if (recipientId == null) {
            throw new IllegalArgumentException("Recipient is required to start a conversation.");
        }
        return conversationRepository.findBetweenUsers(senderId, recipientId)
                .orElseGet(() -> createConversation(senderId, recipientId));
    }

    private Conversation createConversation(Long userId, Long otherUserId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        UserEntity other = userRepository.findById(otherUserId)
                .orElseThrow(() -> new RuntimeException("Recipient not found"));

        Conversation conversation = new Conversation();
        conversation.setLastMessageAt(OffsetDateTime.now());
        Conversation saved = conversationRepository.save(conversation);

        ConversationParticipant p1 = new ConversationParticipant();
        p1.setConversation(saved);
        p1.setUser(user);

        ConversationParticipant p2 = new ConversationParticipant();
        p2.setConversation(saved);
        p2.setUser(other);

        participantRepository.saveAll(List.of(p1, p2));
        return saved;
    }

    private void ensureParticipant(Long conversationId, Long userId) {
        if (!participantRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new RuntimeException("User is not part of this conversation.");
        }
    }

    private ConversationDto toConversationDto(Conversation conversation, Long viewerId) {
        List<ConversationParticipant> participants = participantRepository.findByConversationId(conversation.getId());
        UserEntity other = participants.stream()
                .map(ConversationParticipant::getUser)
                .filter(user -> !user.getId().equals(viewerId))
                .findFirst()
                .orElse(null);

        String lastMessage = messageRepository.findTopByConversationIdOrderByCreatedAtDesc(conversation.getId())
                .map(Message::getBody)
                .orElse(null);

        return new ConversationDto(
                conversation.getId(),
                other != null ? other.getId() : null,
                other != null ? other.getUsername() : null,
                other != null ? other.getDisplayName() : null,
                lastMessage,
                conversation.getLastMessageAt()
        );
    }

    private MessageDto toMessageDto(Message message) {
        return new MessageDto(
                message.getId(),
                message.getConversation().getId(),
                message.getSender().getId(),
                message.getBody(),
                message.getCreatedAt()
        );
    }
}
