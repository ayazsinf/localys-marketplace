package com.localys.marketplace.service;

import com.localys.marketplace.dto.OrderCreateRequest;
import com.localys.marketplace.dto.OrderDto;
import com.localys.marketplace.dto.ReviewDto;
import com.localys.marketplace.dto.VendorReviewsDto;
import com.localys.marketplace.model.Address;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.Vendor;
import com.localys.marketplace.model.enums.NotificationType;
import com.localys.marketplace.repository.NotificationRepository;
import com.localys.marketplace.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewIntegrationTest extends IntegrationTest {

    @Autowired
    private ReviewService reviewService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private NotificationRepository notificationRepository;

    private UserEntity seller;
    private Vendor vendor;
    private UserEntity buyer;
    private Address address;

    @BeforeEach
    void setUp() {
        seller = data.user("seller");
        vendor = data.vendor(seller);
        buyer = data.user("buyer");
        address = data.address(buyer);
    }

    @Test
    void buyerReviewsDeliveredItemOnce() {
        Long orderId = paidOrder();
        Long itemId = orderService.getForBuyer(buyer.getId(), orderId).items().get(0).orderItemId();

        assertThatThrownBy(() -> reviewService.create(buyer.getId(), itemId, 5, null))
                .hasMessage("Review not allowed before delivery");

        orderService.shipItems(seller.getId(), orderId, "TRACK");
        orderService.confirmDelivery(buyer.getId(), orderId);

        UserEntity stranger = data.user("stranger");
        assertThatThrownBy(() -> reviewService.create(stranger.getId(), itemId, 5, null))
                .hasMessage("Order not found");
        assertThatThrownBy(() -> reviewService.create(buyer.getId(), itemId, 6, null))
                .hasMessage("Rating must be between 1 and 5");
        assertThatThrownBy(() -> reviewService.create(buyer.getId(), itemId, 4, "x".repeat(1001)))
                .hasMessage("Review comment is too long");

        ReviewDto review = reviewService.create(buyer.getId(), itemId, 4, "  Fast shipping  ");
        assertThat(review.comment()).isEqualTo("Fast shipping");
        assertThat(review.reviewerName()).isEqualTo("buyer");

        assertThatThrownBy(() -> reviewService.create(buyer.getId(), itemId, 5, null))
                .hasMessage("Item already reviewed");

        OrderDto order = orderService.getForBuyer(buyer.getId(), orderId);
        assertThat(order.items().get(0).myRating()).isEqualTo(4);
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(seller.getId()))
                .anyMatch(notification -> notification.getType() == NotificationType.REVIEW_RECEIVED);
    }

    @Test
    void vendorSummaryAveragesAllReviews() {
        assertThat(reviewService.forVendor(vendor.getId(), null, null).reviewCount()).isZero();

        review(5);
        review(4);
        review(4);

        VendorReviewsDto reviews = reviewService.forVendor(vendor.getId(), 0, 2);
        assertThat(reviews.reviewCount()).isEqualTo(3);
        assertThat(reviews.averageRating()).isEqualTo(4.3);
        assertThat(reviews.reviews().items()).hasSize(2);
        assertThat(reviews.reviews().totalPages()).isEqualTo(2);
    }

    private void review(int rating) {
        Long orderId = paidOrder();
        orderService.shipItems(seller.getId(), orderId, "TRACK");
        orderService.confirmDelivery(buyer.getId(), orderId);
        Long itemId = orderService.getForBuyer(buyer.getId(), orderId).items().get(0).orderItemId();
        reviewService.create(buyer.getId(), itemId, rating, null);
    }

    private Long paidOrder() {
        Product product = data.listing(vendor, "20.00", 1);
        data.addToCart(buyer, product, 1);
        Long orderId = orderService.createOrder(buyer.getId(), new OrderCreateRequest(
                List.of(product.getId()), address.getId(), "mondial", "card")).id();
        orderService.markPaid(orderId);
        return orderId;
    }
}
