package com.localys.marketplace.controller;

import com.localys.marketplace.dto.NotificationDto;
import com.localys.marketplace.model.CustomUserDetails;
import com.localys.marketplace.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationDto> list(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return notificationService.listForUser(userDetails.getUserId());
    }

    @GetMapping("/unread-count")
    public long unreadCount(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return notificationService.countUnread(userDetails.getUserId());
    }

    @PostMapping("/{notificationId}/read")
    public void markRead(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("notificationId") Long notificationId
    ) {
        notificationService.markRead(userDetails.getUserId(), notificationId);
    }

    @PostMapping("/read-all")
    public void markAllRead(@AuthenticationPrincipal CustomUserDetails userDetails) {
        notificationService.markAllRead(userDetails.getUserId());
    }
}
