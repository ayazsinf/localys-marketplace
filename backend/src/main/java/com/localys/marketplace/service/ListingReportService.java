package com.localys.marketplace.service;

import com.localys.marketplace.dto.AdminReportDto;
import com.localys.marketplace.model.ListingReport;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.enums.ModerationStatus;
import com.localys.marketplace.model.enums.ReportReason;
import com.localys.marketplace.model.enums.ReportStatus;
import com.localys.marketplace.repository.ListingReportRepository;
import com.localys.marketplace.repository.ProductRepository;
import com.localys.marketplace.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Listing reports: any signed-in user can flag a public listing; admins review the queue and
 * either dismiss the report or remove the listing, which closes every open report on it.
 */
@Service
public class ListingReportService {

    static final int MAX_DETAILS_LENGTH = 1000;

    private final ListingReportRepository reportRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductService productService;
    private final AdminRecipientService adminRecipientService;
    private final NotificationService notificationService;

    public ListingReportService(
            ListingReportRepository reportRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            ProductService productService,
            AdminRecipientService adminRecipientService,
            NotificationService notificationService
    ) {
        this.reportRepository = reportRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.productService = productService;
        this.adminRecipientService = adminRecipientService;
        this.notificationService = notificationService;
    }

    @Transactional
    public void report(Long reporterId, Long productId, String rawReason, String details) {
        ReportReason reason = ReportReason.from(rawReason)
                .orElseThrow(() -> new IllegalArgumentException("Invalid report reason"));
        String text = details == null ? null : details.trim();
        if (text != null && text.length() > MAX_DETAILS_LENGTH) {
            throw new IllegalArgumentException("Report details are too long");
        }
        if (reason == ReportReason.OTHER && (text == null || text.isEmpty())) {
            throw new IllegalArgumentException("Report details are required");
        }
        Product product = productId == null ? null : productRepository
                .findPublicById(productId, ModerationStatus.APPROVED, OffsetDateTime.now())
                .orElse(null);
        if (product == null) {
            throw new IllegalArgumentException("Product not found");
        }
        if (product.getVendor() != null
                && product.getVendor().getUser() != null
                && reporterId.equals(product.getVendor().getUser().getId())) {
            throw new IllegalArgumentException("Cannot report own product");
        }
        if (reportRepository.existsByProduct_IdAndReporter_IdAndStatus(productId, reporterId, ReportStatus.OPEN)) {
            throw new IllegalArgumentException("Listing already reported");
        }
        UserEntity reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        ListingReport report = new ListingReport();
        report.setProduct(product);
        report.setReporter(reporter);
        report.setReason(reason);
        report.setDetails(text == null || text.isEmpty() ? null : text);
        reportRepository.save(report);

        for (UserEntity admin : adminRecipientService.findAdmins()) {
            notificationService.createListingReportedNotification(admin, product);
        }
    }

    @Transactional(readOnly = true)
    public List<AdminReportDto> listForAdmin(String status) {
        List<ListingReport> reports = "ALL".equalsIgnoreCase(status)
                ? reportRepository.findAllByOrderByCreatedAtDesc()
                : reportRepository.findByStatusOrderByCreatedAtDesc(parseStatus(status));
        List<Long> productIds = reports.stream().map(report -> report.getProduct().getId()).distinct().toList();
        Map<Long, Long> openCounts = productIds.isEmpty()
                ? Map.of()
                : reportRepository.countByProduct(productIds, ReportStatus.OPEN).stream()
                .collect(Collectors.toMap(
                        ListingReportRepository.ProductReportCount::getProductId,
                        ListingReportRepository.ProductReportCount::getCount));
        return reports.stream()
                .map(report -> toDto(report, openCounts.getOrDefault(report.getProduct().getId(), 0L)))
                .toList();
    }

    @Transactional
    public void dismiss(Long reportId, UserEntity admin) {
        ListingReport report = openReport(reportId);
        resolve(report, ReportStatus.DISMISSED, admin);
    }

    /** Removes the reported listing and closes every open report about it. */
    @Transactional
    public void removeListing(Long reportId, String note, UserEntity admin) {
        ListingReport report = openReport(reportId);
        Long productId = report.getProduct().getId();
        String removalNote = note == null || note.isBlank()
                ? "Reported: " + report.getReason().name()
                : note.trim();
        productService.removeProductByAdmin(productId, removalNote, admin);
        for (ListingReport open : reportRepository.findByProduct_IdAndStatus(productId, ReportStatus.OPEN)) {
            resolve(open, ReportStatus.ACTIONED, admin);
        }
    }

    private ListingReport openReport(Long reportId) {
        ListingReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found"));
        if (report.getStatus() != ReportStatus.OPEN) {
            throw new IllegalArgumentException("Report already resolved");
        }
        return report;
    }

    private void resolve(ListingReport report, ReportStatus status, UserEntity admin) {
        report.setStatus(status);
        report.setResolvedBy(admin);
        report.setResolvedAt(OffsetDateTime.now());
        reportRepository.save(report);
    }

    private ReportStatus parseStatus(String raw) {
        try {
            return ReportStatus.valueOf(raw == null ? "OPEN" : raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid report status");
        }
    }

    private AdminReportDto toDto(ListingReport report, long openReportsForListing) {
        UserEntity reporter = report.getReporter();
        String reporterName = reporter.getDisplayName() != null && !reporter.getDisplayName().isBlank()
                ? reporter.getDisplayName()
                : reporter.getUsername();
        return new AdminReportDto(
                report.getId(),
                report.getProduct().getId(),
                report.getProduct().getName(),
                report.getProduct().isActive(),
                report.getReason().name(),
                report.getDetails(),
                reporterName,
                report.getStatus().name(),
                report.getCreatedAt(),
                report.getResolvedAt(),
                openReportsForListing
        );
    }
}
