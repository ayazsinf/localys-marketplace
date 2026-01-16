package com.localys.marketplace.controller;

import com.localys.marketplace.dto.ConversationDto;
import com.localys.marketplace.dto.MessageDto;
import com.localys.marketplace.dto.SendMessageRequest;
import com.localys.marketplace.model.CustomUserDetails;
import com.localys.marketplace.service.MessageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final MessageService messageService;

    public ConversationController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public List<ConversationDto> listConversations(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return messageService.listConversations(userDetails.getUserId());
    }

    @PostMapping("/with/{userId}")
    public ConversationDto getOrCreateConversation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("userId") Long userId
    ) {
        return messageService.getOrCreateConversation(userDetails.getUserId(), userId);
    }

    @GetMapping("/{conversationId}/messages")
    public List<MessageDto> getMessages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("conversationId") Long conversationId,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return messageService.getMessages(userDetails.getUserId(), conversationId, limit);
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<MessageDto> sendMessage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("conversationId") Long conversationId,
            @RequestBody SendMessageRequest request
    ) {
        MessageDto saved = messageService.sendMessage(
                userDetails.getUserId(),
                conversationId,
                request.recipientId(),
                request.body()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
