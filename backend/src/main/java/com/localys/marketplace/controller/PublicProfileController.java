package com.localys.marketplace.controller;

import com.localys.marketplace.dto.PublicProfileDto;
import com.localys.marketplace.service.PublicProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicProfileController {

    private final PublicProfileService publicProfileService;

    public PublicProfileController(PublicProfileService publicProfileService) {
        this.publicProfileService = publicProfileService;
    }

    /** Public: a user's profile page data; listings come from GET /api/products?sellerId=. */
    @GetMapping("/api/users/{userId}/public")
    public PublicProfileDto get(@PathVariable("userId") Long userId) {
        return publicProfileService.get(userId);
    }
}
