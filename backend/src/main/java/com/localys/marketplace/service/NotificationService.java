package com.localys.marketplace.service;

import com.localys.marketplace.dto.NotificationDto;
import com.localys.marketplace.model.Notification;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.enums.NotificationType;
import com.localys.marketplace.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;

    public NotificationService(NotificationRepository notificationRepository, EmailService emailService) {
        this.notificationRepository = notificationRepository;
        this.emailService = emailService;
    }

    public void createProductCreatedNotification(UserEntity user, Product product) {
        if (user == null || product == null) {
            return;
        }
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(NotificationType.PRODUCT_CREATED);
        notification.setTitle("Listing created");
        notification.setMessage("Your listing \"" + product.getName() + "\" is now live.");
        notification.setLink("/listings");
        notification.setRead(false);
        notificationRepository.save(notification);
        emailService.sendProductCreatedEmail(user, product);
    }

    public void createFavoriteAddedNotification(UserEntity recipient, UserEntity actor, Product product) {
        if (recipient == null || actor == null || product == null) {
            return;
        }
        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setType(NotificationType.FAVORITE_ADDED);
        notification.setTitle("Favori eklendi");
        notification.setMessage(formatFavoriteMessage(actor, product));
        notification.setLink("/products/" + product.getId());
        notification.setRead(false);
        notificationRepository.save(notification);
        emailService.sendFavoriteAddedEmail(recipient, actor, product);
    }

    public List<NotificationDto> listForUser(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDto)
                .toList();
    }

    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    public void markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        if (!notification.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Notification not found");
        }
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(OffsetDateTime.now());
            notificationRepository.save(notification);
        }
    }

    public void markAllRead(Long userId) {
        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        OffsetDateTime now = OffsetDateTime.now();
        for (Notification notification : notifications) {
            if (!notification.isRead()) {
                notification.setRead(true);
                notification.setReadAt(now);
            }
        }
        notificationRepository.saveAll(notifications);
    }

    private NotificationDto toDto(Notification notification) {
        return new NotificationDto(
                notification.getId(),
                notification.getType().name(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getLink(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }

    private String formatFavoriteMessage(UserEntity actor, Product product) {
        String name = actor.getDisplayName();
        if (name == null || name.isBlank()) {
            name = actor.getUsername();
        }
        if (name == null || name.isBlank()) {
            name = "Someone";
        }
        return name + " \"" + product.getName() + "\" urununu favorilerine ekledi.";
    }
}
