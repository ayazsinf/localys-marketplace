package com.localys.marketplace.service;

import com.localys.marketplace.dto.SavedSearchDto;
import com.localys.marketplace.dto.SavedSearchRequest;
import com.localys.marketplace.model.Category;
import com.localys.marketplace.model.SavedSearch;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.repository.CategoryRepository;
import com.localys.marketplace.repository.SavedSearchRepository;
import com.localys.marketplace.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Saved home-page searches. Alerts are sent by {@link SavedSearchAlertService}.
 */
@Service
public class SavedSearchService {

    static final int MAX_SAVED_SEARCHES = 20;
    static final int MAX_NAME_LENGTH = 80;
    static final int MAX_QUERY_LENGTH = 200;

    private final SavedSearchRepository savedSearchRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    public SavedSearchService(
            SavedSearchRepository savedSearchRepository,
            UserRepository userRepository,
            CategoryRepository categoryRepository
    ) {
        this.savedSearchRepository = savedSearchRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<SavedSearchDto> list(Long userId) {
        return savedSearchRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public SavedSearchDto create(Long userId, SavedSearchRequest request) {
        String name = trimToNull(request.name());
        if (name == null || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Saved search name is required (max 80 characters)");
        }
        String query = trimToNull(request.query());
        if (query != null && query.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("Saved search query is too long");
        }
        BigDecimal minPrice = request.minPrice();
        BigDecimal maxPrice = request.maxPrice();
        if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)
                || (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)) {
            throw new IllegalArgumentException("Saved search price range is invalid");
        }
        if (query == null && request.categoryId() == null && minPrice == null && maxPrice == null) {
            throw new IllegalArgumentException("Saved search needs at least one filter");
        }
        if (savedSearchRepository.countByUser_Id(userId) >= MAX_SAVED_SEARCHES) {
            throw new IllegalArgumentException("Saved search limit reached");
        }
        Category category = request.categoryId() == null ? null : categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new IllegalArgumentException("Saved search category not found"));
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        SavedSearch search = new SavedSearch();
        search.setUser(user);
        search.setName(name);
        search.setQuery(query);
        search.setCategory(category);
        search.setMinPrice(minPrice);
        search.setMaxPrice(maxPrice);
        search.setAlertsEnabled(request.alertsEnabled() == null || request.alertsEnabled());
        // Only listings approved from now on count as new for this search.
        search.setLastCheckedAt(OffsetDateTime.now());
        return toDto(savedSearchRepository.save(search));
    }

    @Transactional
    public SavedSearchDto setAlerts(Long userId, Long searchId, boolean enabled) {
        SavedSearch search = owned(userId, searchId);
        if (enabled && !search.isAlertsEnabled()) {
            // Do not report everything that was approved while alerts were off.
            search.setLastCheckedAt(OffsetDateTime.now());
        }
        search.setAlertsEnabled(enabled);
        return toDto(savedSearchRepository.save(search));
    }

    @Transactional
    public void delete(Long userId, Long searchId) {
        savedSearchRepository.delete(owned(userId, searchId));
    }

    private SavedSearch owned(Long userId, Long searchId) {
        return savedSearchRepository.findByIdAndUser_Id(searchId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Saved search not found"));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private SavedSearchDto toDto(SavedSearch search) {
        return new SavedSearchDto(
                search.getId(),
                search.getName(),
                search.getQuery(),
                search.getCategory() != null ? search.getCategory().getId() : null,
                search.getCategory() != null ? search.getCategory().getName() : null,
                search.getMinPrice(),
                search.getMaxPrice(),
                search.isAlertsEnabled(),
                search.getCreatedAt()
        );
    }
}
