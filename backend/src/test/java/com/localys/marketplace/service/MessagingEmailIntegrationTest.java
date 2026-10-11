package com.localys.marketplace.service;

import com.localys.marketplace.dto.ConversationDto;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.enums.EmailCategory;
import com.localys.marketplace.repository.UserRepository;
import com.localys.marketplace.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestPropertySource(properties = {
        "app.mail.enabled=true",
        "app.public-url=https://localys.test/"
})
class MessagingEmailIntegrationTest extends IntegrationTest {

    @MockBean
    private MailDispatcher mailDispatcher;
    @MockBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private MessageService messageService;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private KeycloakPrincipalService principalService;
    @Autowired
    private UserRepository userRepository;

    private UserEntity seller;
    private UserEntity buyer;
    private Product product;

    @BeforeEach
    void setUp() {
        seller = data.user("seller");
        product = data.listing(data.vendor(seller), "25.00", 1);
        buyer = data.user("buyer");
        clearInvocations(mailDispatcher);
    }

    @Test
    void emailsOnlyTheFirstUnreadMessageOfAConversation() {
        ConversationDto conversation = messageService.getOrCreateConversation(buyer.getId(), seller.getId(), product.getId());

        messageService.sendMessage(buyer.getId(), conversation.id(), null, "Is it still available?");
        messageService.sendMessage(buyer.getId(), conversation.id(), null, "I can pick it up today.");

        verify(mailDispatcher, times(1)).send(anyString(), eq(seller.getEmail()),
                contains(product.getName()), contains("https://localys.test/messages?conversationId=" + conversation.id()));

        // Once the seller has read the thread, the next message is emailed again.
        messageService.markRead(seller.getId(), conversation.id());
        messageService.sendMessage(buyer.getId(), conversation.id(), null, "Hello?");
        verify(mailDispatcher, times(2)).send(anyString(), eq(seller.getEmail()), anyString(), anyString());
        // The conversation never exposes the other user's username.
        assertThat(messageService.listConversations(seller.getId()))
                .filteredOn(dto -> dto.id().equals(conversation.id()))
                .singleElement()
                .satisfies(dto -> assertThat(dto.otherDisplayName()).isEqualTo("buyer"));
    }

    @Test
    void notificationsAreMirroredByEmailWithAnAbsoluteLink() {
        notificationService.createOrderPaidNotification(buyer, 42L);

        verify(mailDispatcher).send(anyString(), eq(buyer.getEmail()), eq("Payment confirmed"),
                contains("https://localys.test/orders/42"));
    }

    @Test
    void optedOutCategoriesAreNotEmailedButStillNotified() {
        buyer.setEmailOptOuts(EmailCategory.format(java.util.EnumSet.of(EmailCategory.ORDERS)));
        userRepository.save(buyer);
        seller.setEmailOptOuts("MESSAGES");
        userRepository.save(seller);

        notificationService.createOrderPaidNotification(buyer, 43L);
        ConversationDto conversation = messageService.getOrCreateConversation(buyer.getId(), seller.getId(), product.getId());
        messageService.sendMessage(buyer.getId(), conversation.id(), null, "Hi");

        verify(mailDispatcher, never()).send(anyString(), eq(buyer.getEmail()), anyString(), anyString());
        verify(mailDispatcher, never()).send(anyString(), eq(seller.getEmail()), anyString(), anyString());
        assertThat(notificationService.listForUser(buyer.getId())).hasSize(1);

        // Other categories still arrive.
        notificationService.createOrderPaidNotification(seller, 44L);
        verify(mailDispatcher).send(anyString(), eq(seller.getEmail()), eq("Payment confirmed"), anyString());
    }

    @Test
    void profileEditsSurviveTheNextLoginAndWelcomeIsSentOnce() {
        String subject = "kc-" + UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("preferred_username", subject + "@mail.test")
                .claim("email", subject + "@mail.test")
                .claim("name", "Token Name")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        when(jwtDecoder.decode("token")).thenReturn(jwt);

        UserEntity created = principalService.authenticate("token").getUser();
        assertThat(created.getDisplayName()).isEqualTo("Token Name");
        verify(mailDispatcher).send(anyString(), eq(subject + "@mail.test"), anyString(), anyString());

        created.setDisplayName("Edited Name");
        created.setPhone("+33600000000");
        userRepository.save(created);

        UserEntity again = principalService.authenticate("token").getUser();
        assertThat(again.getDisplayName()).isEqualTo("Edited Name");
        assertThat(again.getPhone()).isEqualTo("+33600000000");
        verify(mailDispatcher, times(1)).send(anyString(), eq(subject + "@mail.test"), anyString(), anyString());
        verify(mailDispatcher, never()).send(anyString(), eq(seller.getEmail()), anyString(), anyString());
    }
}
