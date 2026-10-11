package com.localys.marketplace.service;

import com.localys.marketplace.dto.ConversationDto;
import com.localys.marketplace.dto.ConversationEventDto;
import com.localys.marketplace.dto.MessageDto;
import com.localys.marketplace.model.Conversation;
import com.localys.marketplace.model.ConversationParticipant;
import com.localys.marketplace.model.Message;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.ProductImage;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.enums.ModerationStatus;
import com.localys.marketplace.repository.ConversationParticipantRepository;
import com.localys.marketplace.repository.ConversationRepository;
import com.localys.marketplace.repository.MessageRepository;
import com.localys.marketplace.repository.ProductRepository;
import com.localys.marketplace.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class MessageService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int MAX_BODY_LENGTH = 4000;
    private static final int MAX_EMAIL_PREVIEW = 300;

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final EmailService emailService;
    private final UserBlockService userBlockService;
    private final MessageSafetyService messageSafetyService;
    private final MediaStorageService mediaStorageService;

    public MessageService(
            ConversationRepository conversationRepository,
            ConversationParticipantRepository participantRepository,
            MessageRepository messageRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            SimpMessagingTemplate messagingTemplate,
            EmailService emailService,
            UserBlockService userBlockService,
            MessageSafetyService messageSafetyService,
            MediaStorageService mediaStorageService
    ) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.messagingTemplate = messagingTemplate;
        this.emailService = emailService;
        this.userBlockService = userBlockService;
        this.messageSafetyService = messageSafetyService;
        this.mediaStorageService = mediaStorageService;
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> listConversations(Long userId) {
        return conversationRepository.findByParticipantUserId(userId).stream()
                .map(conversation -> toConversationDto(conversation, userId))
                .sorted(Comparator.comparing(ConversationDto::lastMessageAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /**
     * Opens (or reuses) the conversation with another user. With a {@code productId} the
     * conversation is about that listing, and the other user must be its seller.
     */
    @Transactional
    public ConversationDto getOrCreateConversation(Long userId, Long otherUserId, Long productId) {
        if (productId == null) {
            Conversation conversation = conversationRepository.findBetweenUsersWithoutProduct(userId, otherUserId).stream()
                    .findFirst()
                    .orElseGet(() -> createConversation(userId, otherUserId, null));
            return toConversationDto(conversation, userId);
        }
        Conversation existing = conversationRepository.findBetweenUsersForProduct(userId, otherUserId, productId).stream()
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return toConversationDto(existing, userId);
        }
        // New conversations can only be started about a listing that is currently public.
        Product product = productRepository.findPublicById(productId, ModerationStatus.APPROVED, OffsetDateTime.now())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        Long sellerUserId = product.getVendor() != null && product.getVendor().getUser() != null
                ? product.getVendor().getUser().getId()
                : null;
        if (!otherUserId.equals(sellerUserId)) {
            throw new IllegalArgumentException("Recipient is not the seller of this listing.");
        }
        return toConversationDto(createConversation(userId, otherUserId, product), userId);
    }

    /** Returns the latest messages and marks the ones received by {@code userId} as read. */
    @Transactional
    public List<MessageDto> getMessages(Long userId, Long conversationId, Integer limit) {
        ensureParticipant(conversationId, userId);
        int pageSize = limit != null && limit > 0 ? Math.min(limit, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
        List<Message> messages = messageRepository
                .findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, pageSize))
                .getContent();
        markReadAndAnnounce(conversationId, userId);
        return messages.stream()
                .sorted(Comparator.comparing(Message::getCreatedAt))
                .map(this::toMessageDto)
                .toList();
    }

    @Transactional
    public void markRead(Long userId, Long conversationId) {
        ensureParticipant(conversationId, userId);
        markReadAndAnnounce(conversationId, userId);
    }

    /** Tells the other participant that {@code userId} is typing; nothing is stored. */
    public void announceTyping(Long userId, Long conversationId) {
        if (conversationId == null) {
            return;
        }
        ensureParticipant(conversationId, userId);
        boolean blocked = participantRepository.findByConversationId(conversationId).stream()
                .map(ConversationParticipant::getUser)
                .anyMatch(user -> !user.getId().equals(userId) && userBlockService.isBlockedBetween(userId, user.getId()));
        if (!blocked) {
            messagingTemplate.convertAndSend(eventsTopic(conversationId),
                    new ConversationEventDto("TYPING", conversationId, userId, OffsetDateTime.now()));
        }
    }

    /** Marks received messages read and, if any were unread, lets the sender know live. */
    private void markReadAndAnnounce(Long conversationId, Long userId) {
        OffsetDateTime now = OffsetDateTime.now();
        if (messageRepository.markConversationRead(conversationId, userId, now) > 0) {
            messagingTemplate.convertAndSend(eventsTopic(conversationId),
                    new ConversationEventDto("READ", conversationId, userId, now));
        }
    }

    private static String eventsTopic(Long conversationId) {
        return "/topic/conversations/" + conversationId + "/events";
    }

    public long countUnread(Long userId) {
        return messageRepository.countUnreadForUser(userId);
    }

    @Transactional
    public MessageDto sendMessage(Long senderId, Long conversationId, Long recipientId, String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Message body cannot be empty.");
        }
        if (body.trim().length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("Message is too long.");
        }
        Conversation conversation = resolveConversation(senderId, conversationId, recipientId);
        ensureParticipant(conversation.getId(), senderId);
        return deliver(senderId, conversation, body.trim(), null);
    }

    /** Sends a photo, with an optional caption, in an existing conversation. */
    @Transactional
    public MessageDto sendImage(Long senderId, Long conversationId, MultipartFile image, String caption) {
        String text = caption == null ? "" : caption.trim();
        if (text.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("Message is too long.");
        }
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        ensureParticipant(conversation.getId(), senderId);
        ensureOtherNotBlocked(senderId, conversation.getId());
        String imageUrl = mediaStorageService.storeMessageImage(conversation.getId(), image);
        return deliver(senderId, conversation, text, imageUrl);
    }

    private void ensureOtherNotBlocked(Long senderId, Long conversationId) {
        participantRepository.findByConversationId(conversationId).stream()
                .map(ConversationParticipant::getUser)
                .filter(user -> !user.getId().equals(senderId))
                .forEach(user -> userBlockService.ensureNotBlocked(senderId, user.getId()));
    }

    private MessageDto deliver(Long senderId, Conversation conversation, String body, String imageUrl) {
        UserEntity sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Email the recipient only for the first unread message, not for every line of a chat.
        UserEntity recipient = participantRepository.findByConversationId(conversation.getId()).stream()
                .map(ConversationParticipant::getUser)
                .filter(user -> !user.getId().equals(senderId))
                .findFirst()
                .orElse(null);
        if (recipient != null) {
            userBlockService.ensureNotBlocked(senderId, recipient.getId());
        }
        boolean recipientWasCaughtUp = recipient != null
                && messageRepository.countUnreadInConversation(conversation.getId(), recipient.getId()) == 0;

        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setBody(body);
        message.setImageUrl(imageUrl);
        MessageSafetyService.Warning warning = messageSafetyService.check(message.getBody());
        message.setSafetyWarning(warning == null ? null : warning.name());

        Message saved = messageRepository.save(message);
        conversation.setLastMessageAt(saved.getCreatedAt());
        conversationRepository.save(conversation);

        MessageDto dto = toMessageDto(saved);
        messagingTemplate.convertAndSend("/topic/conversations/" + conversation.getId(), dto);
        if (recipientWasCaughtUp) {
            String text = saved.getBody().isEmpty() && saved.getImageUrl() != null ? "📷" : saved.getBody();
            String preview = text.length() > MAX_EMAIL_PREVIEW
                    ? text.substring(0, MAX_EMAIL_PREVIEW) + "…"
                    : text;
            emailService.sendNewMessageEmail(recipient, sender,
                    conversation.getProduct() != null ? conversation.getProduct().getName() : null,
                    preview, conversation.getId());
        }
        return dto;
    }

    private Conversation resolveConversation(Long senderId, Long conversationId, Long recipientId) {
        if (conversationId != null) {
            return conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        }
        if (recipientId == null) {
            throw new IllegalArgumentException("Recipient is required to start a conversation.");
        }
        return conversationRepository.findBetweenUsersWithoutProduct(senderId, recipientId).stream()
                .findFirst()
                .orElseGet(() -> createConversation(senderId, recipientId, null));
    }

    private Conversation createConversation(Long userId, Long otherUserId, Product product) {
        if (userId.equals(otherUserId)) {
            throw new IllegalArgumentException("Cannot start a conversation with yourself.");
        }
        userBlockService.ensureNotBlocked(userId, otherUserId);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        UserEntity other = userRepository.findById(otherUserId)
                .orElseThrow(() -> new IllegalArgumentException("Recipient not found"));

        Conversation conversation = new Conversation();
        conversation.setProduct(product);
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
            throw new AccessDeniedException("User is not part of this conversation.");
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
                .map(message -> message.getBody().isEmpty() && message.getImageUrl() != null ? "📷" : message.getBody())
                .orElse(null);

        Product product = conversation.getProduct();
        String productImageUrl = product == null ? null : product.getImages().stream()
                .sorted(Comparator.comparingInt(ProductImage::getSortOrder))
                .map(ProductImage::getUrl)
                .findFirst()
                .orElse(null);

        return new ConversationDto(
                conversation.getId(),
                other != null ? other.getId() : null,
                other != null ? other.getDisplayName() : null,
                lastMessage,
                conversation.getLastMessageAt(),
                product != null ? product.getId() : null,
                product != null ? product.getName() : null,
                productImageUrl,
                messageRepository.countUnreadInConversation(conversation.getId(), viewerId),
                other != null && userBlockService.hasBlocked(viewerId, other.getId()),
                other != null && userBlockService.isBlockedBetween(viewerId, other.getId())
        );
    }

    private MessageDto toMessageDto(Message message) {
        return new MessageDto(
                message.getId(),
                message.getConversation().getId(),
                message.getSender().getId(),
                message.getBody(),
                message.getImageUrl(),
                message.getCreatedAt(),
                message.getSafetyWarning(),
                message.getReadAt()
        );
    }
}
