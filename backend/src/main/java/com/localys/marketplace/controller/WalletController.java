package com.localys.marketplace.controller;

import com.localys.marketplace.dto.WalletDto;
import com.localys.marketplace.model.CustomUserDetails;
import com.localys.marketplace.service.SellerPayoutService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** A seller's earnings and payout setup. */
@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final SellerPayoutService sellerPayoutService;

    public WalletController(SellerPayoutService sellerPayoutService) {
        this.sellerPayoutService = sellerPayoutService;
    }

    /** Also checks whether a seller who started payout setup has finished it. */
    @GetMapping
    public WalletDto wallet(@AuthenticationPrincipal CustomUserDetails user) {
        sellerPayoutService.refreshAccount(user.getUserId());
        return sellerPayoutService.wallet(user.getUserId());
    }

    /** Returns the Stripe page where the seller sets up payouts. */
    @PostMapping("/onboarding")
    public Map<String, String> onboarding(@AuthenticationPrincipal CustomUserDetails user) {
        return Map.of("url", sellerPayoutService.startOnboarding(user.getUserId()));
    }
}
