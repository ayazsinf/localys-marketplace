package com.localys.marketplace.service;

import com.localys.marketplace.dto.SavedSearchDto;
import com.localys.marketplace.dto.SavedSearchRequest;
import com.localys.marketplace.model.Notification;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.Vendor;
import com.localys.marketplace.model.enums.NotificationType;
import com.localys.marketplace.repository.NotificationRepository;
import com.localys.marketplace.repository.ProductRepository;
import com.localys.marketplace.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SavedSearchIntegrationTest extends IntegrationTest {

    @Autowired
    private SavedSearchService savedSearchService;
    @Autowired
    private SavedSearchAlertService alertService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    private UserEntity user;
    private Vendor otherVendor;
    /** A word unique to this test run so other tests' listings never match. */
    private String keyword;

    @BeforeEach
    void setUp() {
        user = data.user("searcher");
        otherVendor = data.vendor(data.user("seller"));
        keyword = "lamp" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void validatesSearches() {
        assertThatThrownBy(() -> savedSearchService.create(user.getId(), request(" ", keyword, null)))
                .hasMessageStartingWith("Saved search name is required");
        assertThatThrownBy(() -> savedSearchService.create(user.getId(),
                new SavedSearchRequest("Nothing", null, null, null, null, null)))
                .hasMessage("Saved search needs at least one filter");
        assertThatThrownBy(() -> savedSearchService.create(user.getId(),
                new SavedSearchRequest("Bad", keyword, null, new BigDecimal("50"), new BigDecimal("10"), null)))
                .hasMessage("Saved search price range is invalid");

        SavedSearchDto saved = savedSearchService.create(user.getId(), request(" Lamps ", "  " + keyword + " ", "50"));
        assertThat(saved.name()).isEqualTo("Lamps");
        assertThat(saved.query()).isEqualTo(keyword);
        assertThat(saved.alertsEnabled()).isTrue();
        assertThat(savedSearchService.list(user.getId())).extracting(SavedSearchDto::id).containsExactly(saved.id());

        UserEntity stranger = data.user("stranger");
        assertThatThrownBy(() -> savedSearchService.delete(stranger.getId(), saved.id()))
                .hasMessage("Saved search not found");

        for (int i = 1; i < SavedSearchService.MAX_SAVED_SEARCHES; i++) {
            savedSearchService.create(user.getId(), request("Search " + i, keyword, null));
        }
        assertThatThrownBy(() -> savedSearchService.create(user.getId(), request("One too many", keyword, null)))
                .hasMessage("Saved search limit reached");
    }

    @Test
    void alertsCountOnlyNewMatchingListingsOnce() {
        listing("Old " + keyword, "20.00", otherVendor, OffsetDateTime.now().minusDays(1));
        SavedSearchDto saved = savedSearchService.create(user.getId(), request("Lamps", keyword, "50"));

        listing("Vintage " + keyword, "30.00", otherVendor, OffsetDateTime.now());
        listing("Brass " + keyword, "45.00", otherVendor, OffsetDateTime.now());
        listing("Designer " + keyword, "80.00", otherVendor, OffsetDateTime.now());
        listing("Chair", "30.00", otherVendor, OffsetDateTime.now());
        // The user's own new listings are not news to them.
        listing("My " + keyword, "10.00", data.vendor(user), OffsetDateTime.now());

        alertService.sendAlerts();
        alertService.sendAlerts();

        List<Notification> alerts = alerts();
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getMessage()).startsWith("2 new listings").contains("\"Lamps\"");
        assertThat(alerts.get(0).getLink()).isEqualTo("/?q=" + keyword + "&maxPrice=50");

        savedSearchService.setAlerts(user.getId(), saved.id(), false);
        listing("Another " + keyword, "25.00", otherVendor, OffsetDateTime.now());
        alertService.sendAlerts();
        assertThat(alerts()).hasSize(1);

        // Re-enabling does not replay listings approved while alerts were off.
        savedSearchService.setAlerts(user.getId(), saved.id(), true);
        alertService.sendAlerts();
        assertThat(alerts()).hasSize(1);
    }

    private SavedSearchRequest request(String name, String query, String maxPrice) {
        return new SavedSearchRequest(name, query, null, null, maxPrice == null ? null : new BigDecimal(maxPrice), null);
    }

    private void listing(String name, String price, Vendor vendor, OffsetDateTime approvedAt) {
        Product product = data.listing(vendor, price, 1);
        product.setName(name);
        product.setReviewedAt(approvedAt);
        productRepository.save(product);
    }

    private List<Notification> alerts() {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(notification -> notification.getType() == NotificationType.SAVED_SEARCH_MATCH)
                .toList();
    }
}
