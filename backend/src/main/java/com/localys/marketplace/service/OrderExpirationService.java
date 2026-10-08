package com.localys.marketplace.service;

import com.localys.marketplace.model.enums.OrderStatus;
import com.localys.marketplace.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Releases stock reserved by orders whose payment never started or was abandoned
 * (e.g. the Stripe session could not be created, or its expiry webhook was lost).
 */
@Service
public class OrderExpirationService {

    private static final Logger logger = LoggerFactory.getLogger(OrderExpirationService.class);

    /**
     * Longer than the Stripe session lifetime, so no session can still be payable when the
     * order is cancelled. {@link StripePaymentService} stops opening sessions for orders older
     * than {@code UNPAID_ORDER_TTL - SESSION_TTL} for the same reason.
     */
    static final Duration UNPAID_ORDER_TTL = Duration.ofMinutes(60);

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    public OrderExpirationService(OrderRepository orderRepository, OrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    @Scheduled(cron = "${app.orders.expiration-cron:0 */10 * * * *}")
    public void cancelAbandonedOrders() {
        OffsetDateTime threshold = OffsetDateTime.now().minus(UNPAID_ORDER_TTL);
        List<Long> orderIds = orderRepository.findIdsByStatusCreatedBefore(OrderStatus.PAYMENT_PENDING, threshold);
        for (Long orderId : orderIds) {
            try {
                orderService.cancelUnpaid(orderId);
            } catch (RuntimeException ex) {
                logger.warn("Could not cancel abandoned order {}: {}", orderId, ex.getMessage());
            }
        }
    }
}
