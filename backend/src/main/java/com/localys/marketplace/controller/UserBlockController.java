package com.localys.marketplace.controller;

import com.localys.marketplace.dto.BlockedUserDto;
import com.localys.marketplace.model.CustomUserDetails;
import com.localys.marketplace.service.UserBlockService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class UserBlockController {

    private final UserBlockService userBlockService;

    public UserBlockController(UserBlockService userBlockService) {
        this.userBlockService = userBlockService;
    }

    @GetMapping("/api/blocks")
    public List<BlockedUserDto> list(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return userBlockService.list(userDetails.getUserId());
    }

    /** Whether the current user blocked this user. */
    @GetMapping("/api/blocks/{userId}")
    public Map<String, Boolean> status(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("userId") Long userId
    ) {
        return Map.of("blocked", userBlockService.hasBlocked(userDetails.getUserId(), userId));
    }

    @PostMapping("/api/blocks/{userId}")
    public ResponseEntity<Void> block(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("userId") Long userId
    ) {
        userBlockService.block(userDetails.getUserId(), userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/blocks/{userId}")
    public ResponseEntity<Void> unblock(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("userId") Long userId
    ) {
        userBlockService.unblock(userDetails.getUserId(), userId);
        return ResponseEntity.noContent().build();
    }
}
