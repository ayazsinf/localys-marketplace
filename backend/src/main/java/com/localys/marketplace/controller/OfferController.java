package com.localys.marketplace.controller;

import com.localys.marketplace.dto.CartDto;
import com.localys.marketplace.dto.OfferDto;
import com.localys.marketplace.model.CustomUserDetails;
import com.localys.marketplace.service.OfferService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/offers")
public class OfferController {

    private final OfferService offerService;

    public OfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    /** {@code role} is "buyer" (offers I made, default) or "seller" (offers on my listings). */
    @GetMapping
    public List<OfferDto> list(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(name = "role", defaultValue = "buyer") String role
    ) {
        return "seller".equalsIgnoreCase(role)
                ? offerService.listForSeller(userDetails.getUserId())
                : offerService.listForBuyer(userDetails.getUserId());
    }

    /** The current user's open offer on a listing, or 204 when there is none. */
    @GetMapping("/product/{productId}")
    public ResponseEntity<OfferDto> mine(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("productId") Long productId
    ) {
        OfferDto offer = offerService.openOfferFor(userDetails.getUserId(), productId);
        return offer == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(offer);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OfferDto make(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody MakeOfferRequest request
    ) {
        return offerService.make(userDetails.getUserId(), request.productId(), request.amount());
    }

    @PostMapping("/{id}/accept")
    public OfferDto accept(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable("id") Long id) {
        return offerService.accept(userDetails.getUserId(), id);
    }

    @PostMapping("/{id}/decline")
    public OfferDto decline(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable("id") Long id) {
        return offerService.decline(userDetails.getUserId(), id);
    }

    @PostMapping("/{id}/counter")
    public OfferDto counter(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id,
            @RequestBody AmountRequest request
    ) {
        return offerService.counter(userDetails.getUserId(), id, request.amount());
    }

    @PostMapping("/{id}/withdraw")
    public OfferDto withdraw(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable("id") Long id) {
        return offerService.withdraw(userDetails.getUserId(), id);
    }

    @PostMapping("/{id}/add-to-cart")
    public CartDto addToCart(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable("id") Long id) {
        return offerService.addToCart(userDetails.getUserId(), id);
    }

    public record MakeOfferRequest(Long productId, BigDecimal amount) {
    }

    public record AmountRequest(BigDecimal amount) {
    }
}
