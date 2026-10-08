package com.localys.marketplace.controller;

import com.localys.marketplace.dto.OrderCreateRequest;
import com.localys.marketplace.dto.OrderDto;
import com.localys.marketplace.dto.OrderResponse;
import com.localys.marketplace.model.CustomUserDetails;
import com.localys.marketplace.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @GetMapping
    public List<OrderDto> listMyOrders(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return orderService.listForBuyer(userDetails.getUserId());
    }

    @GetMapping("/sales")
    public List<OrderDto> listMySales(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return orderService.listSales(userDetails.getUserId());
    }

    @GetMapping("/{id}")
    public OrderDto getMyOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id
    ) {
        return orderService.getForBuyer(userDetails.getUserId(), id);
    }

    /** Seller: mark their items of this order as shipped. */
    @PostMapping("/{id}/ship")
    public OrderDto shipOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id,
            @RequestBody ShipOrderRequest request
    ) {
        return orderService.shipItems(userDetails.getUserId(), id, request.trackingNumber());
    }

    /** Buyer: confirm the shipped items were received. */
    @PostMapping("/{id}/confirm-delivery")
    public OrderDto confirmDelivery(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id
    ) {
        return orderService.confirmDelivery(userDetails.getUserId(), id);
    }

    public record ShipOrderRequest(String trackingNumber) {
    }
}
