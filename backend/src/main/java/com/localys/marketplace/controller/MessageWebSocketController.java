package com.localys.marketplace.controller;

import com.localys.marketplace.dto.MessageDto;
import com.localys.marketplace.dto.SendMessageRequest;
import com.localys.marketplace.model.CustomUserDetails;
import com.localys.marketplace.service.MessageService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class MessageWebSocketController {

    private final MessageService messageService;

    public MessageWebSocketController(MessageService messageService) {
        this.messageService = messageService;
    }

    @MessageMapping("/messages.send")
    public MessageDto sendMessage(@Payload SendMessageRequest request, Principal principal) {
        CustomUserDetails userDetails = resolveUser(principal);
        return messageService.sendMessage(
                userDetails.getUserId(),
                request.conversationId(),
                request.recipientId(),
                request.body()
        );
    }

    private CustomUserDetails resolveUser(Principal principal) {
        if (principal instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof CustomUserDetails details) {
            return details;
        }
        throw new RuntimeException("Authenticated user not found.");
    }
}
