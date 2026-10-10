package com.localys.marketplace.service;

import com.localys.marketplace.dto.OrderCreateRequest;
import com.localys.marketplace.dto.OrderDto;
import com.localys.marketplace.dto.ReviewDto;
import com.localys.marketplace.dto.UserReviewsDto;
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
    void buyerAndSellerRateEachOtherOnceAfterDelivery() {
        Long orderId = paidOrder();
        Long itemId = orderService.getForBuyer(buyer.getId(), orderId).items().get(0).orderItemId();

        assertThatThrownBy(() -> reviewService.create(buyer.getId(), itemId, 5, null))
                .hasMessage("Review not allowed before delivery");
        assertThatThrownBy(() -> reviewService.create(seller.getId(), itemId, 5, null))
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

        ReviewDto ofSeller = reviewService.create(buyer.getId(), itemId, 4, "  Fast shipping  ");
        assertThat(ofSeller.comment()).isEqualTo("Fast shipping");
        assertThat(ofSeller.reviewerName()).isEqualTo("buyer");
        assertThat(ofSeller.revieweeRole()).isEqualTo("SELLER");
        assertThatThrownBy(() -> reviewService.create(buyer.getId(), itemId, 5, null))
                .hasMessage("Item already reviewed");

        ReviewDto ofBuyer = reviewService.create(seller.getId(), itemId, 5, "Quick payment");
        assertThat(ofBuyer.revieweeRole()).isEqualTo("BUYER");
        assertThatThrownBy(() -> reviewService.create(seller.getId(), itemId, 5, null))
                .hasMessage("Item already reviewed");

        // Each side sees the rating they gave.
        assertThat(orderService.getForBuyer(buyer.getId(), orderId).items().get(0).myRating()).isEqualTo(4);
        OrderDto sale = orderService.listSales(seller.getId()).stream()
                .filter(order -> order.id().equals(orderId)).findFirst().orElseThrow();
        assertThat(sale.items().get(0).myRating()).isEqualTo(5);
        assertThat(sale.buyerUserId()).isEqualTo(buyer.getId());

        assertThat(reviewService.forUser(seller.getId(), null, null, null).asSeller().count()).isEqualTo(1);
        assertThat(reviewService.forUser(buyer.getId(), null, null, null).asBuyer().average()).isEqualTo(5.0);
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(seller.getId()))
                .anyMatch(notification -> notification.getType() == NotificationType.REVIEW_RECEIVED
                        && notification.getLink().equals("/users/" + seller.getId()));
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(buyer.getId()))
                .anyMatch(notification -> notification.getType() == NotificationType.REVIEW_RECEIVED);
    }

    @Test
    void userRatingCombinesBothRoles() {
        assertThat(reviewService.forUser(seller.getId(), null, null, null).overall().count()).isZero();

        rateSeller(5);
        rateSeller(4);
        rateSeller(4);
        // The seller also bought something and was rated as a buyer.
        UserEntity otherSeller = data.user("other-seller");
        Vendor otherVendor = data.vendor(otherSeller);
        Address sellerAddress = data.address(seller);
        Product product = data.listing(otherVendor, "10.00", 1);
        data.addToCart(seller, product, 1);
        Long orderId = orderService.createOrder(seller.getId(), new OrderCreateRequest(
                List.of(product.getId()), sellerAddress.getId(), "mondial", "card")).id();
        orderService.markPaid(orderId);
        orderService.shipItems(otherSeller.getId(), orderId, "TRACK");
        orderService.confirmDelivery(seller.getId(), orderId);
        Long itemId = orderService.getForBuyer(seller.getId(), orderId).items().get(0).orderItemId();
        reviewService.create(otherSeller.getId(), itemId, 1, null);

        UserReviewsDto reviews = reviewService.forUser(seller.getId(), null, 0, 2);
        assertThat(reviews.asSeller().count()).isEqualTo(3);
        assertThat(reviews.asSeller().average()).isEqualTo(4.3);
        assertThat(reviews.asBuyer().count()).isEqualTo(1);
        assertThat(reviews.overall().count()).isEqualTo(4);
        assertThat(reviews.overall().average()).isEqualTo(3.5);
        assertThat(reviews.reviews().items()).hasSize(2);
        assertThat(reviews.reviews().totalPages()).isEqualTo(2);
        assertThat(reviewService.forUser(seller.getId(), "BUYER", 0, 10).reviews().items())
                .extracting(ReviewDto::revieweeRole).containsExactly("BUYER");
        assertThat(reviewService.summary(seller.getId()).average()).isEqualTo(3.5);
    }

    private void rateSeller(int rating) {
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
