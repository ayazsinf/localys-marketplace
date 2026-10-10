package com.localys.marketplace.controller;

import com.localys.marketplace.dto.ReportCreateRequest;
import com.localys.marketplace.model.CustomUserDetails;
import com.localys.marketplace.service.ListingReportService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportController {

    private final ListingReportService reportService;

    public ReportController(ListingReportService reportService) {
        this.reportService = reportService;
    }

    /** Signed-in users flag a listing for admin review. */
    @PostMapping("/api/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public void report(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody ReportCreateRequest request
    ) {
        reportService.report(userDetails.getUserId(), request.productId(), request.reason(), request.details());
    }
}
