package com.localys.marketplace.service;

import com.localys.marketplace.dto.PageResponse;
import com.localys.marketplace.dto.ReviewDto;
import com.localys.marketplace.dto.VendorReviewsDto;
import com.localys.marketplace.model.OrderItem;
import com.localys.marketplace.model.Review;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.repository.OrderItemRepository;
import com.localys.marketplace.repository.ReviewRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Seller ratings. Only the buyer of an order item can review it, once, after confirming
 * delivery, so every rating comes from a completed purchase.
 */
@Service
public class ReviewService {

    static final int MAX_COMMENT_LENGTH = 1000;
    private static final int MAX_PAGE_SIZE = 50;

    private final ReviewRepository reviewRepository;
    private final OrderItemRepository orderItemRepository;
    private final NotificationService notificationService;

    public ReviewService(
            ReviewRepository reviewRepository,
            OrderItemRepository orderItemRepository,
            NotificationService notificationService
    ) {
        this.reviewRepository = reviewRepository;
        this.orderItemRepository = orderItemRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public ReviewDto create(Long buyerId, Long orderItemId, Integer rating, String comment) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        String text = comment == null ? null : comment.trim();
        if (text != null && text.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("Review comment is too long");
        }
        OrderItem item = orderItemId == null ? null : orderItemRepository.findById(orderItemId)
                .filter(found -> found.getOrder().getUser().getId().equals(buyerId))
                .orElse(null);
        if (item == null) {
            throw new IllegalArgumentException("Order not found");
        }
        if (item.getDeliveredAt() == null) {
            throw new IllegalArgumentException("Review not allowed before delivery");
        }
        if (reviewRepository.existsByOrderItem_Id(item.getId())) {
            throw new IllegalArgumentException("Item already reviewed");
        }

        Review review = new Review();
        review.setOrderItem(item);
        review.setReviewer(item.getOrder().getUser());
        review.setVendor(item.getVendor());
        review.setRating(rating);
        review.setComment(text == null || text.isEmpty() ? null : text);
        Review saved = reviewRepository.save(review);

        if (item.getVendor().getUser() != null) {
            notificationService.createReviewReceivedNotification(
                    item.getVendor().getUser(), item.getProductNameSnapshot(), rating);
        }
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public VendorReviewsDto forVendor(Long vendorId, Integer page, Integer size) {
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size <= 0 ? 10 : Math.min(size, MAX_PAGE_SIZE);
        Summary summary = summary(vendorId);
        PageResponse<ReviewDto> reviews = PageResponse.from(
                reviewRepository.findByVendor_IdOrderByCreatedAtDesc(vendorId, PageRequest.of(pageNumber, pageSize)),
                this::toDto
        );
        return new VendorReviewsDto(summary.average(), summary.count(), reviews);
    }

    /** Average rounded to one decimal; 0 when the seller has no reviews yet. */
    @Transactional(readOnly = true)
    public Summary summary(Long vendorId) {
        ReviewRepository.RatingSummary summary = reviewRepository.summarize(vendorId);
        double average = summary.getAverage() == null ? 0 : Math.round(summary.getAverage() * 10) / 10.0;
        return new Summary(average, summary.getCount());
    }

    /** Rating given for each of the order items that has been reviewed. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> ratingsByOrderItem(Collection<Long> orderItemIds) {
        if (orderItemIds.isEmpty()) {
            return Map.of();
        }
        return reviewRepository.findByOrderItem_IdIn(orderItemIds).stream()
                .collect(Collectors.toMap(review -> review.getOrderItem().getId(), Review::getRating));
    }

    private ReviewDto toDto(Review review) {
        return new ReviewDto(
                review.getId(),
                review.getRating(),
                review.getComment(),
                publicName(review.getReviewer()),
                review.getOrderItem().getProductNameSnapshot(),
                review.getCreatedAt()
        );
    }

    /** Reviews are public, so only the display name is shown, never the username or email. */
    private String publicName(UserEntity user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }
        return null;
    }

    public record Summary(double average, long count) {
    }
}
