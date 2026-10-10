package com.localys.marketplace.service;

import com.localys.marketplace.dto.PublicProfileDto;
import com.localys.marketplace.dto.UserReviewsDto;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.repository.MessageRepository;
import com.localys.marketplace.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Builds the public profile shown to other users: identity, ratings, listings and how
 * responsive the user is in messages.
 */
@Service
public class PublicProfileService {

    static final Duration RESPONSE_WINDOW = Duration.ofDays(90);
    /** Below this many conversations the response stats say too little to show. */
    static final int MIN_CONVERSATIONS_FOR_STATS = 3;

    private final UserRepository userRepository;
    private final ReviewService reviewService;
    private final ProductSearchService productSearchService;
    private final MessageRepository messageRepository;

    public PublicProfileService(
            UserRepository userRepository,
            ReviewService reviewService,
            ProductSearchService productSearchService,
            MessageRepository messageRepository
    ) {
        this.userRepository = userRepository;
        this.reviewService = reviewService;
        this.productSearchService = productSearchService;
        this.messageRepository = messageRepository;
    }

    @Transactional(readOnly = true)
    public PublicProfileDto get(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .filter(UserEntity::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Page<Product> listings = productSearchService.search(new ProductSearchService.Criteria(
                null, null, null, null, "NEWEST", 0, 1, userId, List.of(), null));
        String location = listings.getContent().stream()
                .map(Product::getLocationText)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .orElse(null);

        UserReviewsDto reviews = reviewService.forUser(userId, null, 0, 1);

        MessageRepository.ResponseStats stats = messageRepository.responseStats(
                userId, OffsetDateTime.now().minus(RESPONSE_WINDOW));
        Integer responseRate = null;
        String responseTime = null;
        if (stats.getReceived() >= MIN_CONVERSATIONS_FOR_STATS) {
            responseRate = (int) Math.round(100.0 * stats.getAnswered() / stats.getReceived());
            responseTime = bucket(stats.getAverageSeconds());
        }

        return new PublicProfileDto(
                user.getId(),
                publicName(user),
                user.getCreatedAt(),
                location,
                listings.getTotalElements(),
                reviews.overall(),
                reviews.asSeller(),
                reviews.asBuyer(),
                responseRate,
                responseTime
        );
    }

    private String bucket(Double averageSeconds) {
        if (averageSeconds == null) {
            return null;
        }
        if (averageSeconds <= Duration.ofHours(1).toSeconds()) {
            return "WITHIN_HOUR";
        }
        if (averageSeconds <= Duration.ofDays(1).toSeconds()) {
            return "WITHIN_DAY";
        }
        return "WITHIN_DAYS";
    }

    /** Never expose the username (it may be an email address); fall back to null. */
    private String publicName(UserEntity user) {
        return user.getDisplayName() != null && !user.getDisplayName().isBlank()
                ? user.getDisplayName()
                : null;
    }
}
