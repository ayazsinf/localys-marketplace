package com.localys.marketplace.service;

import com.localys.marketplace.model.SavedSearch;
import com.localys.marketplace.repository.SavedSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Periodically tells users how many new listings match their saved searches. One notification
 * per search and run keeps busy searches from flooding the inbox.
 */
@Service
public class SavedSearchAlertService {

    private static final Logger logger = LoggerFactory.getLogger(SavedSearchAlertService.class);

    private final SavedSearchRepository savedSearchRepository;
    private final ProductSearchService productSearchService;
    private final NotificationService notificationService;
    private final TransactionTemplate transactionTemplate;

    public SavedSearchAlertService(
            SavedSearchRepository savedSearchRepository,
            ProductSearchService productSearchService,
            NotificationService notificationService,
            TransactionTemplate transactionTemplate
    ) {
        this.savedSearchRepository = savedSearchRepository;
        this.productSearchService = productSearchService;
        this.notificationService = notificationService;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(cron = "${app.saved-searches.alert-cron:0 0 * * * *}")
    public void sendAlerts() {
        OffsetDateTime runAt = OffsetDateTime.now();
        List<Long> searchIds = savedSearchRepository.findIdsWithAlertsEnabled();
        for (Long searchId : searchIds) {
            try {
                // One transaction per search so a failure only skips that search until the next run.
                transactionTemplate.executeWithoutResult(status -> checkSearch(searchId, runAt));
            } catch (RuntimeException ex) {
                logger.warn("Could not check saved search {}: {}", searchId, ex.getMessage());
            }
        }
    }

    void checkSearch(Long searchId, OffsetDateTime runAt) {
        SavedSearch search = savedSearchRepository.findById(searchId).orElse(null);
        if (search == null || !search.isAlertsEnabled() || !search.getLastCheckedAt().isBefore(runAt)) {
            return;
        }
        ProductSearchService.Criteria criteria = ProductSearchService.basic(
                search.getQuery(),
                search.getCategory() != null ? search.getCategory().getId() : null,
                search.getMinPrice(),
                search.getMaxPrice()
        );
        long matches = productSearchService.countApprovedBetween(
                criteria, search.getLastCheckedAt(), runAt, search.getUser().getId());
        if (matches > 0) {
            notificationService.createSavedSearchMatchNotification(search.getUser(), search, matches);
        }
        search.setLastCheckedAt(runAt);
        savedSearchRepository.save(search);
    }
}
