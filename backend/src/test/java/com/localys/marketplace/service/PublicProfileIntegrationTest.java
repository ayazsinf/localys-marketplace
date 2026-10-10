package com.localys.marketplace.service;

import com.localys.marketplace.dto.PublicProfileDto;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.Vendor;
import com.localys.marketplace.repository.ProductRepository;
import com.localys.marketplace.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicProfileIntegrationTest extends IntegrationTest {

    @Autowired
    private PublicProfileService publicProfileService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserEntity seller;
    private Vendor vendor;

    @BeforeEach
    void setUp() {
        seller = data.user("Marie");
        vendor = data.vendor(seller);
    }

    @Test
    void showsListingsAndLocationWithoutPrivateData() {
        Product older = data.listing(vendor, "10.00", 1);
        older.setLocationText("Lyon");
        productRepository.save(older);
        Product newer = data.listing(vendor, "12.00", 1);
        newer.setLocationText("Paris");
        productRepository.save(newer);
        Product removed = data.listing(vendor, "12.00", 1);
        removed.setActive(false);
        productRepository.save(removed);

        PublicProfileDto profile = publicProfileService.get(seller.getId());

        assertThat(profile.displayName()).isEqualTo("Marie");
        assertThat(profile.activeListingCount()).isEqualTo(2);
        assertThat(profile.location()).isEqualTo("Paris");
        assertThat(profile.rating().count()).isZero();
        assertThat(profile.responseRate()).isNull();
        assertThatThrownBy(() -> publicProfileService.get(-1L)).hasMessage("User not found");
    }

    @Test
    void responseStatsCountOnlyConversationsOthersStarted() {
        OffsetDateTime now = OffsetDateTime.now();
        // Three buyers wrote first; the seller answered two of them within minutes.
        conversation(data.user("b1"), now.minusHours(5), now.minusHours(5).plusMinutes(10));
        conversation(data.user("b2"), now.minusHours(4), now.minusHours(4).plusMinutes(20));
        conversation(data.user("b3"), now.minusHours(3), null);
        // A conversation the seller started does not count.
        Long own = newConversation(data.user("b4"));
        message(own, seller.getId(), now.minusHours(2));

        PublicProfileDto profile = publicProfileService.get(seller.getId());

        assertThat(profile.responseRate()).isEqualTo(67);
        assertThat(profile.responseTime()).isEqualTo("WITHIN_HOUR");
    }

    private void conversation(UserEntity buyer, OffsetDateTime askedAt, OffsetDateTime repliedAt) {
        Long conversationId = newConversation(buyer);
        message(conversationId, buyer.getId(), askedAt);
        if (repliedAt != null) {
            message(conversationId, seller.getId(), repliedAt);
        }
    }

    private Long newConversation(UserEntity other) {
        Long id = jdbcTemplate.queryForObject(
                "insert into conversations (created_at, updated_at) values (now(), now()) returning id", Long.class);
        for (Long userId : new Long[]{seller.getId(), other.getId()}) {
            jdbcTemplate.update("insert into conversation_participants (conversation_id, user_id, created_at, updated_at) "
                    + "values (?, ?, now(), now())", id, userId);
        }
        return id;
    }

    private void message(Long conversationId, Long senderId, OffsetDateTime at) {
        jdbcTemplate.update("insert into messages (conversation_id, sender_id, body, created_at, updated_at) "
                + "values (?, ?, 'hello', ?, ?)", conversationId, senderId, at, at);
    }
}
