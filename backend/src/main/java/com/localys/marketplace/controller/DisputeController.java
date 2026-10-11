package com.localys.marketplace.controller;

import com.localys.marketplace.dto.DisputeDto;
import com.localys.marketplace.model.CustomUserDetails;
import com.localys.marketplace.service.BuyerProtectionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Buyer protection disputes: opened by buyers, answered by sellers, settled by admins. */
@RestController
public class DisputeController {

    private final BuyerProtectionService buyerProtectionService;

    public DisputeController(BuyerProtectionService buyerProtectionService) {
        this.buyerProtectionService = buyerProtectionService;
    }

    @PostMapping("/api/orders/{orderId}/items/{itemId}/dispute")
    public ResponseEntity<DisputeDto> open(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable("orderId") Long orderId,
            @PathVariable("itemId") Long itemId,
            @RequestBody OpenDisputeRequest request
    ) {
        DisputeDto dispute = buyerProtectionService.openDispute(
                user.getUserId(), orderId, itemId, request.reason(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(dispute);
    }

    /** {@code role} buyer (default) or seller. */
    @GetMapping("/api/disputes")
    public List<DisputeDto> list(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(name = "role", defaultValue = "buyer") String role
    ) {
        return "seller".equalsIgnoreCase(role)
                ? buyerProtectionService.listForSeller(user.getUserId())
                : buyerProtectionService.listForBuyer(user.getUserId());
    }

    @PostMapping("/api/disputes/{id}/withdraw")
    public DisputeDto withdraw(@AuthenticationPrincipal CustomUserDetails user, @PathVariable("id") Long id) {
        return buyerProtectionService.withdraw(user.getUserId(), id);
    }

    @PostMapping("/api/disputes/{id}/refund")
    public DisputeDto refund(@AuthenticationPrincipal CustomUserDetails user, @PathVariable("id") Long id) {
        return buyerProtectionService.refundBySeller(user.getUserId(), id);
    }

    @PostMapping("/api/disputes/{id}/contest")
    public DisputeDto contest(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable("id") Long id,
            @RequestBody ContestRequest request
    ) {
        return buyerProtectionService.contest(user.getUserId(), id, request.response());
    }

    /** {@code status} ACTIVE (default) or ALL. */
    @GetMapping("/api/admin/disputes")
    @PreAuthorize("hasRole('ADMIN')")
    public List<DisputeDto> adminList(@RequestParam(name = "status", defaultValue = "ACTIVE") String status) {
        return buyerProtectionService.listForAdmin(status);
    }

    /** {@code outcome} REFUND (pay the buyer back) or RELEASE (pay the seller). */
    @PostMapping("/api/admin/disputes/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public DisputeDto resolve(
            @AuthenticationPrincipal CustomUserDetails admin,
            @PathVariable("id") Long id,
            @RequestBody ResolveRequest request
    ) {
        return buyerProtectionService.resolve(admin.getUserId(), id, request.outcome(), request.note());
    }

    public record OpenDisputeRequest(String reason, String description) {
    }

    public record ContestRequest(String response) {
    }

    public record ResolveRequest(String outcome, String note) {
    }
}
