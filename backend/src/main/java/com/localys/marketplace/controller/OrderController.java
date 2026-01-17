package com.localys.marketplace.controller;

import com.localys.marketplace.dto.OrderCreateRequest;
import com.localys.marketplace.dto.OrderResponse;
import com.localys.marketplace.model.CustomUserDetails;
import com.localys.marketplace.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody OrderCreateRequest request
    ) {
        return ResponseEntity.ok(orderService.createOrder(userDetails.getUserId(), request));
    }
}
