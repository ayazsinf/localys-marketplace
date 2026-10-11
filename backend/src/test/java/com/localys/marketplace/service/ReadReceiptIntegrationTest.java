package com.localys.marketplace.service;

import com.localys.marketplace.dto.ConversationDto;
import com.localys.marketplace.dto.ConversationEventDto;
import com.localys.marketplace.dto.MessageDto;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ReadReceiptIntegrationTest extends IntegrationTest {

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MessageService messageService;
    @Autowired
    private UserBlockService userBlockService;

    private UserEntity seller;
    private UserEntity buyer;
    private ConversationDto conversation;
    private String eventsTopic;

    @BeforeEach
    void setUp() {
        seller = data.user("seller");
        buyer = data.user("buyer");
        Product product = data.listing(data.vendor(seller), "25.00", 1);
        conversation = messageService.getOrCreateConversation(buyer.getId(), seller.getId(), product.getId());
        eventsTopic = "/topic/conversations/" + conversation.id() + "/events";
        clearInvocations(messagingTemplate);
    }

    @Test
    void readingAConversationTellsTheSender() {
        MessageDto sent = messageService.sendMessage(buyer.getId(), conversation.id(), null, "Still available?");
        assertThat(sent.readAt()).isNull();

        // The sender re-opening the thread does not mark their own message read.
        messageService.getMessages(buyer.getId(), conversation.id(), null);
        verify(messagingTemplate, never()).convertAndSend(eq(eventsTopic), any(Object.class));

        messageService.getMessages(seller.getId(), conversation.id(), null);
        verify(messagingTemplate, times(1)).convertAndSend(eq(eventsTopic), argThat((Object event) ->
                event instanceof ConversationEventDto e && e.type().equals("READ") && e.userId().equals(seller.getId())));

        // Nothing new to read: no second event.
        messageService.markRead(seller.getId(), conversation.id());
        verify(messagingTemplate, times(1)).convertAndSend(eq(eventsTopic), any(Object.class));
        assertThat(messageService.getMessages(buyer.getId(), conversation.id(), null).get(0).readAt()).isNotNull();
    }

    @Test
    void typingIsRelayedOnlyBetweenParticipantsWhoAreNotBlocked() {
        messageService.announceTyping(buyer.getId(), conversation.id());
        verify(messagingTemplate).convertAndSend(eq(eventsTopic), argThat((Object event) ->
                event instanceof ConversationEventDto e && e.type().equals("TYPING") && e.userId().equals(buyer.getId())));

        UserEntity stranger = data.user("stranger");
        assertThatThrownBy(() -> messageService.announceTyping(stranger.getId(), conversation.id()))
                .isInstanceOf(AccessDeniedException.class);

        clearInvocations(messagingTemplate);
        userBlockService.block(seller.getId(), buyer.getId());
        messageService.announceTyping(buyer.getId(), conversation.id());
        verify(messagingTemplate, never()).convertAndSend(eq(eventsTopic), any(Object.class));
    }
}
