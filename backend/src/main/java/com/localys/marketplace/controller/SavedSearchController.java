package com.localys.marketplace.controller;

import com.localys.marketplace.dto.SavedSearchDto;
import com.localys.marketplace.dto.SavedSearchRequest;
import com.localys.marketplace.model.CustomUserDetails;
import com.localys.marketplace.service.SavedSearchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/saved-searches")
public class SavedSearchController {

    private final SavedSearchService savedSearchService;

    public SavedSearchController(SavedSearchService savedSearchService) {
        this.savedSearchService = savedSearchService;
    }

    @GetMapping
    public List<SavedSearchDto> list(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return savedSearchService.list(userDetails.getUserId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedSearchDto create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody SavedSearchRequest request
    ) {
        return savedSearchService.create(userDetails.getUserId(), request);
    }

    @PatchMapping("/{id}")
    public SavedSearchDto setAlerts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id,
            @RequestBody AlertsRequest request
    ) {
        return savedSearchService.setAlerts(userDetails.getUserId(), id, request.alertsEnabled());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id
    ) {
        savedSearchService.delete(userDetails.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    public record AlertsRequest(boolean alertsEnabled) {
    }
}
