package com.localys.marketplace.service;

import com.localys.marketplace.dto.AdminReportDto;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.enums.ListingRemovalReason;
import com.localys.marketplace.model.enums.NotificationType;
import com.localys.marketplace.model.enums.USER_ROLE;
import com.localys.marketplace.repository.NotificationRepository;
import com.localys.marketplace.repository.ProductRepository;
import com.localys.marketplace.repository.UserRepository;
import com.localys.marketplace.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListingReportIntegrationTest extends IntegrationTest {

    @Autowired
    private ListingReportService reportService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    private UserEntity seller;
    private UserEntity admin;
    private UserEntity reporter;
    private Product product;

    @BeforeEach
    void setUp() {
        seller = data.user("seller");
        product = data.listing(data.vendor(seller), "15.00", 1);
        admin = data.user("admin");
        admin.setRole(USER_ROLE.ROLE_ADMIN);
        admin = userRepository.save(admin);
        reporter = data.user("reporter");
    }

    @Test
    void reportIsValidatedAndQueuedForAdmins() {
        assertThatThrownBy(() -> reportService.report(reporter.getId(), product.getId(), "nonsense", null))
                .hasMessage("Invalid report reason");
        assertThatThrownBy(() -> reportService.report(reporter.getId(), product.getId(), "OTHER", "  "))
                .hasMessage("Report details are required");
        assertThatThrownBy(() -> reportService.report(seller.getId(), product.getId(), "SCAM", null))
                .hasMessage("Cannot report own product");

        reportService.report(reporter.getId(), product.getId(), "scam", " Asks for payment outside ");

        assertThatThrownBy(() -> reportService.report(reporter.getId(), product.getId(), "SCAM", null))
                .hasMessage("Listing already reported");
        AdminReportDto queued = openReportFor(product);
        assertThat(queued.reason()).isEqualTo("SCAM");
        assertThat(queued.details()).isEqualTo("Asks for payment outside");
        assertThat(queued.openReportsForListing()).isEqualTo(1);
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(admin.getId()))
                .anyMatch(notification -> notification.getType() == NotificationType.LISTING_REPORTED);
    }

    @Test
    void dismissingKeepsTheListingAndAllowsReportingAgain() {
        reportService.report(reporter.getId(), product.getId(), "MISLEADING", null);
        AdminReportDto report = openReportFor(product);

        reportService.dismiss(report.id(), admin);

        assertThat(productRepository.findById(product.getId()).orElseThrow().isActive()).isTrue();
        assertThatThrownBy(() -> reportService.dismiss(report.id(), admin))
                .hasMessage("Report already resolved");
        reportService.report(reporter.getId(), product.getId(), "MISLEADING", null);
    }

    @Test
    void removingTheListingClosesEveryOpenReport() {
        UserEntity secondReporter = data.user("second-reporter");
        reportService.report(reporter.getId(), product.getId(), "PROHIBITED_ITEM", null);
        reportService.report(secondReporter.getId(), product.getId(), "OFFENSIVE", null);
        AdminReportDto report = openReportFor(product);
        assertThat(report.openReportsForListing()).isEqualTo(2);

        reportService.removeListing(report.id(), null, admin);

        Product removed = productRepository.findById(product.getId()).orElseThrow();
        assertThat(removed.isActive()).isFalse();
        assertThat(removed.getRemovalReason()).isEqualTo(ListingRemovalReason.ADMIN_REMOVED);
        assertThat(reportService.listForAdmin("OPEN"))
                .noneMatch(open -> open.productId().equals(product.getId()));
        assertThat(reportService.listForAdmin("ACTIONED"))
                .filteredOn(closed -> closed.productId().equals(product.getId()))
                .hasSize(2);
        // A removed listing is no longer public, so it cannot be reported again.
        assertThatThrownBy(() -> reportService.report(reporter.getId(), product.getId(), "SCAM", null))
                .hasMessage("Product not found");
    }

    private AdminReportDto openReportFor(Product listing) {
        List<AdminReportDto> open = reportService.listForAdmin("OPEN").stream()
                .filter(report -> report.productId().equals(listing.getId()))
                .toList();
        assertThat(open).isNotEmpty();
        return open.get(0);
    }
}
