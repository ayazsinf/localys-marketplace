package com.localys.marketplace.controller;

import com.localys.marketplace.dto.StripeCheckoutResponse;
import com.localys.marketplace.model.CustomUserDetails;
import com.localys.marketplace.service.StripePaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/stripe")
public class StripePaymentController {

    private final StripePaymentService stripePaymentService;

    public StripePaymentController(StripePaymentService stripePaymentService) {
        this.stripePaymentService = stripePaymentService;
    }

    @PostMapping("/checkout/{orderId}")
    public ResponseEntity<StripeCheckoutResponse> createCheckoutSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("orderId") Long orderId
    ) {
        return ResponseEntity.ok(stripePaymentService.createCheckoutSession(userDetails.getUserId(), orderId));
    }
}
