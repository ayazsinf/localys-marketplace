package com.localys.marketplace.controller;

import com.localys.marketplace.dto.AdminReportDto;
import com.localys.marketplace.dto.RemoveListingRequest;
import com.localys.marketplace.model.CustomUserDetails;
import com.localys.marketplace.service.ListingReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    private final ListingReportService reportService;

    public AdminReportController(ListingReportService reportService) {
        this.reportService = reportService;
    }

    /** {@code status} is OPEN (default), DISMISSED, ACTIONED or ALL. */
    @GetMapping
    public List<AdminReportDto> list(@RequestParam(name = "status", defaultValue = "OPEN") String status) {
        return reportService.listForAdmin(status);
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<Void> dismiss(
            @AuthenticationPrincipal CustomUserDetails admin,
            @PathVariable("id") Long id
    ) {
        reportService.dismiss(id, admin.getUser());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/remove-listing")
    public ResponseEntity<Void> removeListing(
            @AuthenticationPrincipal CustomUserDetails admin,
            @PathVariable("id") Long id,
            @RequestBody(required = false) RemoveListingRequest request
    ) {
        reportService.removeListing(id, request != null ? request.note() : null, admin.getUser());
        return ResponseEntity.noContent().build();
    }
}
