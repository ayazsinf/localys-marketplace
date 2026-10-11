package com.localys.marketplace.service;

import com.localys.marketplace.dto.BlockedUserDto;
import com.localys.marketplace.dto.ConversationDto;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserBlockIntegrationTest extends IntegrationTest {

    @Autowired
    private UserBlockService userBlockService;
    @Autowired
    private MessageService messageService;
    @Autowired
    private OfferService offerService;

    private UserEntity seller;
    private UserEntity buyer;
    private Product product;

    @BeforeEach
    void setUp() {
        seller = data.user("seller");
        buyer = data.user("buyer");
        product = data.listing(data.vendor(seller), "100.00", 1);
    }

    @Test
    void blockingStopsMessagesAndOffersBothWays() {
        ConversationDto conversation = messageService.getOrCreateConversation(buyer.getId(), seller.getId(), product.getId());
        messageService.sendMessage(buyer.getId(), conversation.id(), null, "Is it still available?");

        userBlockService.block(seller.getId(), buyer.getId());
        userBlockService.block(seller.getId(), buyer.getId()); // idempotent

        assertThat(userBlockService.list(seller.getId())).extracting(BlockedUserDto::userId).containsExactly(buyer.getId());
        assertThat(userBlockService.list(buyer.getId())).isEmpty();

        assertThatThrownBy(() -> messageService.sendMessage(buyer.getId(), conversation.id(), null, "Hello?"))
                .hasMessage("User is blocked");
        assertThatThrownBy(() -> messageService.sendMessage(seller.getId(), conversation.id(), null, "Bye"))
                .hasMessage("User is blocked");
        assertThatThrownBy(() -> messageService.sendMessage(buyer.getId(), null, seller.getId(), "New chat"))
                .hasMessage("User is blocked");
        assertThatThrownBy(() -> offerService.make(buyer.getId(), product.getId(), new BigDecimal("80")))
                .hasMessage("User is blocked");

        // The history stays readable, and each side knows who blocked whom.
        assertThat(messageService.getMessages(buyer.getId(), conversation.id(), null)).hasSize(1);
        ConversationDto sellerView = messageService.listConversations(seller.getId()).get(0);
        assertThat(sellerView.blockedByMe()).isTrue();
        assertThat(sellerView.blocked()).isTrue();
        ConversationDto buyerView = messageService.listConversations(buyer.getId()).get(0);
        assertThat(buyerView.blockedByMe()).isFalse();
        assertThat(buyerView.blocked()).isTrue();

        // Only the blocker can lift the block.
        userBlockService.unblock(buyer.getId(), seller.getId());
        assertThat(userBlockService.isBlockedBetween(buyer.getId(), seller.getId())).isTrue();
        userBlockService.unblock(seller.getId(), buyer.getId());
        assertThat(messageService.sendMessage(buyer.getId(), conversation.id(), null, "Hello again").body())
                .isEqualTo("Hello again");
        assertThat(offerService.make(buyer.getId(), product.getId(), new BigDecimal("80")).status()).isEqualTo("PENDING");
    }

    @Test
    void cannotBlockYourself() {
        assertThatThrownBy(() -> userBlockService.block(buyer.getId(), buyer.getId()))
                .hasMessage("Cannot block yourself");
    }
}
