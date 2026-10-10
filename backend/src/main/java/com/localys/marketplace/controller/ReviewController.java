package com.localys.marketplace.controller;

import com.localys.marketplace.dto.ReviewCreateRequest;
import com.localys.marketplace.dto.ReviewDto;
import com.localys.marketplace.dto.UserReviewsDto;
import com.localys.marketplace.model.CustomUserDetails;
import com.localys.marketplace.service.ReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** Buyer rates the seller, or seller rates the buyer, of a delivered order item. */
    @PostMapping("/api/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewDto create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody ReviewCreateRequest request
    ) {
        return reviewService.create(userDetails.getUserId(), request.orderItemId(), request.rating(), request.comment());
    }

    /** Public: a user's ratings and latest reviews; {@code role} is SELLER, BUYER or empty for both. */
    @GetMapping("/api/users/{userId}/reviews")
    public UserReviewsDto forUser(
            @PathVariable("userId") Long userId,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        return reviewService.forUser(userId, role, page, size);
    }
}
