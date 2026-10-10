package com.localys.marketplace.service;

import com.localys.marketplace.dto.PageResponse;
import com.localys.marketplace.dto.ReviewDto;
import com.localys.marketplace.dto.UserReviewsDto;
import com.localys.marketplace.model.OrderItem;
import com.localys.marketplace.model.Review;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.enums.ReviewRole;
import com.localys.marketplace.repository.OrderItemRepository;
import com.localys.marketplace.repository.ReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mutual user ratings, as on classifieds sites: once an item is delivered the buyer rates the
 * seller and the seller rates the buyer, once each. Ratings live on the user's public profile.
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
    public ReviewDto create(Long reviewerId, Long orderItemId, Integer rating, String comment) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        String text = comment == null ? null : comment.trim();
        if (text != null && text.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("Review comment is too long");
        }
        OrderItem item = orderItemId == null ? null : orderItemRepository.findById(orderItemId).orElse(null);
        if (item == null) {
            throw new IllegalArgumentException("Order not found");
        }
        UserEntity buyer = item.getOrder().getUser();
        UserEntity seller = item.getVendor() != null ? item.getVendor().getUser() : null;
        UserEntity reviewer;
        UserEntity reviewee;
        ReviewRole revieweeRole;
        if (buyer.getId().equals(reviewerId)) {
            reviewer = buyer;
            reviewee = seller;
            revieweeRole = ReviewRole.SELLER;
        } else if (seller != null && seller.getId().equals(reviewerId)) {
            reviewer = seller;
            reviewee = buyer;
            revieweeRole = ReviewRole.BUYER;
        } else {
            throw new IllegalArgumentException("Order not found");
        }
        if (reviewee == null) {
            throw new IllegalArgumentException("Order not found");
        }
        if (item.getDeliveredAt() == null) {
            throw new IllegalArgumentException("Review not allowed before delivery");
        }
        if (reviewRepository.existsByOrderItem_IdAndReviewer_Id(item.getId(), reviewerId)) {
            throw new IllegalArgumentException("Item already reviewed");
        }

        Review review = new Review();
        review.setOrderItem(item);
        review.setReviewer(reviewer);
        review.setReviewee(reviewee);
        review.setRevieweeRole(revieweeRole);
        review.setRating(rating);
        review.setComment(text == null || text.isEmpty() ? null : text);
        Review saved = reviewRepository.save(review);

        notificationService.createReviewReceivedNotification(reviewee, item.getProductNameSnapshot(), rating);
        return toDto(saved);
    }

    /** {@code role} optionally limits the page to reviews received as SELLER or BUYER. */
    @Transactional(readOnly = true)
    public UserReviewsDto forUser(Long userId, String role, Integer page, Integer size) {
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size <= 0 ? 10 : Math.min(size, MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(pageNumber, pageSize);
        ReviewRole roleFilter = parseRole(role);
        Page<Review> reviews = roleFilter == null
                ? reviewRepository.findByReviewee_IdOrderByCreatedAtDesc(userId, pageable)
                : reviewRepository.findByReviewee_IdAndRevieweeRoleOrderByCreatedAtDesc(userId, roleFilter, pageable);

        Map<ReviewRole, ReviewRepository.RoleSummary> byRole = reviewRepository.summarizeByRole(userId).stream()
                .collect(Collectors.toMap(ReviewRepository.RoleSummary::getRole, summary -> summary));
        return new UserReviewsDto(
                overall(byRole.values()),
                rating(byRole.get(ReviewRole.SELLER)),
                rating(byRole.get(ReviewRole.BUYER)),
                PageResponse.from(reviews, this::toDto)
        );
    }

    /** Overall rating of a user across both roles, average rounded to one decimal. */
    @Transactional(readOnly = true)
    public UserReviewsDto.RatingDto summary(Long userId) {
        return overall(reviewRepository.summarizeByRole(userId));
    }

    /** Rating the given reviewer already gave for each of these order items. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> ratingsGivenBy(Long reviewerId, Collection<Long> orderItemIds) {
        if (orderItemIds.isEmpty()) {
            return Map.of();
        }
        return reviewRepository.findByOrderItem_IdInAndReviewer_Id(orderItemIds, reviewerId).stream()
                .collect(Collectors.toMap(review -> review.getOrderItem().getId(), Review::getRating));
    }

    private ReviewRole parseRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        try {
            return ReviewRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private UserReviewsDto.RatingDto overall(Collection<ReviewRepository.RoleSummary> summaries) {
        long count = 0;
        double total = 0;
        for (ReviewRepository.RoleSummary summary : summaries) {
            count += summary.getCount();
            total += summary.getAverage() * summary.getCount();
        }
        return new UserReviewsDto.RatingDto(count == 0 ? 0 : round(total / count), count);
    }

    private UserReviewsDto.RatingDto rating(ReviewRepository.RoleSummary summary) {
        return summary == null
                ? new UserReviewsDto.RatingDto(0, 0)
                : new UserReviewsDto.RatingDto(round(summary.getAverage()), summary.getCount());
    }

    private double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private ReviewDto toDto(Review review) {
        return new ReviewDto(
                review.getId(),
                review.getRating(),
                review.getComment(),
                review.getReviewer().getId(),
                publicName(review.getReviewer()),
                review.getRevieweeRole().name(),
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
}
