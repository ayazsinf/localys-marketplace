package com.localys.marketplace.service;

import com.localys.marketplace.dto.ConversationDto;
import com.localys.marketplace.dto.MessageDto;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestPropertySource(properties = "app.media.upload-dir=target/test-uploads")
class MessageImageIntegrationTest extends IntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired
    private MessageService messageService;
    @Autowired
    private UserBlockService userBlockService;

    private UserEntity seller;
    private UserEntity buyer;
    private ConversationDto conversation;

    @BeforeEach
    void setUp() {
        seller = data.user("seller");
        buyer = data.user("buyer");
        Product product = data.listing(data.vendor(seller), "25.00", 1);
        conversation = messageService.getOrCreateConversation(buyer.getId(), seller.getId(), product.getId());
    }

    @Test
    void photosAreStoredAndShownInTheConversation() {
        MessageDto sent = messageService.sendImage(seller.getId(), conversation.id(), image(PNG), "  The scratch  ");

        assertThat(sent.body()).isEqualTo("The scratch");
        assertThat(sent.imageUrl()).startsWith("/uploads/messages/" + conversation.id() + "/").endsWith(".png");
        assertThat(Files.exists(Path.of("target/test-uploads", sent.imageUrl().substring("/uploads/".length())))).isTrue();

        MessageDto photoOnly = messageService.sendImage(seller.getId(), conversation.id(), image(PNG), null);
        assertThat(photoOnly.body()).isEmpty();
        assertThat(messageService.listConversations(buyer.getId()).get(0).lastMessage()).isEqualTo("📷");
        assertThat(messageService.getMessages(buyer.getId(), conversation.id(), null))
                .extracting(MessageDto::imageUrl)
                .containsExactly(sent.imageUrl(), photoOnly.imageUrl());
    }

    @Test
    void rejectsNonImagesOutsidersAndBlockedUsers() {
        assertThatThrownBy(() -> messageService.sendImage(buyer.getId(), conversation.id(), image("<html>".getBytes()), null))
                .hasMessage("Only image uploads are allowed");
        UserEntity stranger = data.user("stranger");
        assertThatThrownBy(() -> messageService.sendImage(stranger.getId(), conversation.id(), image(PNG), null))
                .isInstanceOf(AccessDeniedException.class);
        userBlockService.block(seller.getId(), buyer.getId());
        assertThatThrownBy(() -> messageService.sendImage(buyer.getId(), conversation.id(), image(PNG), null))
                .hasMessage("User is blocked");
    }

    private MockMultipartFile image(byte[] content) {
        return new MockMultipartFile("image", "photo.png", "image/png", content);
    }
}
