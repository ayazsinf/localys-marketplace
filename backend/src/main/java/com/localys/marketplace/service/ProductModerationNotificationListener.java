package com.localys.marketplace.service;

import com.localys.marketplace.event.ProductModerationEvent;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.enums.ProductModerationEventType;
import com.localys.marketplace.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ProductModerationNotificationListener {

    private static final Logger logger = LoggerFactory.getLogger(ProductModerationNotificationListener.class);

    private final ProductRepository productRepository;
    private final NotificationService notificationService;
    private final AdminRecipientService adminRecipientService;

    public ProductModerationNotificationListener(
            ProductRepository productRepository,
            NotificationService notificationService,
            AdminRecipientService adminRecipientService
    ) {
        this.productRepository = productRepository;
        this.notificationService = notificationService;
        this.adminRecipientService = adminRecipientService;
    }

    @Async
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    @TransactionalEventListener(fallbackExecution = true)
    public void onProductModerationEvent(ProductModerationEvent event) {
        Product product = productRepository.findById(event.productId())
                .orElse(null);
        if (product == null) {
            logger.warn("Product moderation event ignored because product {} was not found", event.productId());
            return;
        }

        UserEntity owner = product.getVendor() != null ? product.getVendor().getUser() : null;
        if (event.type() == ProductModerationEventType.SUBMITTED) {
            notificationService.createProductCreatedNotification(owner, product);
            adminRecipientService.findAdmins()
                    .forEach(admin -> notificationService.createProductPendingReviewNotification(admin, product));
            return;
        }

        if (event.type() == ProductModerationEventType.APPROVED) {
            notificationService.createProductApprovedNotification(owner, product);
            return;
        }

        if (event.type() == ProductModerationEventType.REJECTED) {
            notificationService.createProductRejectedNotification(owner, product);
        }
    }
}
