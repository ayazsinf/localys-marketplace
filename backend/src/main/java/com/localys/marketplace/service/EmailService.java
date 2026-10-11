package com.localys.marketplace.service;

import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.enums.EmailCategory;
import com.localys.marketplace.model.enums.NotificationType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailService {


    private final MailDispatcher mailDispatcher;
    private final LocalizationService localizationService;
    private final boolean enabled;
    private final String from;
    private final String publicUrl;

    public EmailService(
            MailDispatcher mailDispatcher,
            LocalizationService localizationService,
            @Value("${app.mail.enabled:false}") boolean enabled,
            @Value("${app.mail.from:no-reply@localys.example}") String from,
            @Value("${app.public-url:http://localhost:4200}") String publicUrl
    ) {
        this.mailDispatcher = mailDispatcher;
        this.localizationService = localizationService;
        this.enabled = enabled;
        this.from = from;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    public void sendWelcomeEmail(UserEntity user) {
        if (user == null) {
            return;
        }
        String subject = localizationService.message("email.welcome.subject");
        String body = localizationService.message("email.welcome.body", safeName(user));
        sendEmail(user.getEmail(), subject, body);
    }

    public void sendProductCreatedEmail(UserEntity user, Product product) {
        if (user == null || product == null || !user.wantsEmail(EmailCategory.LISTINGS)) {
            return;
        }
        String subject = localizationService.message("email.product.submitted.subject");
        String body = localizationService.message("email.product.submitted.body", product.getName());
        sendEmail(user.getEmail(), subject, body);
    }

    public void sendAdminProductPendingEmail(UserEntity admin, Product product) {
        if (admin == null || product == null) {
            return;
        }
        String subject = localizationService.message("email.product.pending-admin.subject");
        String body = localizationService.message("email.product.pending-admin.body", product.getName(), product.getSku());
        sendEmail(admin.getEmail(), subject, body);
    }

    public void sendProductApprovedEmail(UserEntity user, Product product) {
        if (user == null || product == null || !user.wantsEmail(EmailCategory.LISTINGS)) {
            return;
        }
        String subject = localizationService.message("email.product.approved.subject");
        String body = localizationService.message("email.product.approved.body", product.getName());
        sendEmail(user.getEmail(), subject, body);
    }

    public void sendProductRejectedEmail(UserEntity user, Product product) {
        if (user == null || product == null || !user.wantsEmail(EmailCategory.LISTINGS)) {
            return;
        }
        String reason = product.getModerationReason();
        String subject = localizationService.message("email.product.rejected.subject");
        String reasonText = reason == null || reason.isBlank()
                ? ""
                : localizationService.message("email.product.rejected.reason", reason);
        String body = localizationService.message("email.product.rejected.body", product.getName(), reasonText);
        sendEmail(user.getEmail(), subject, body);
    }

    public void sendFavoriteAddedEmail(UserEntity recipient, UserEntity actor, Product product) {
        if (recipient == null || actor == null || product == null || !recipient.wantsEmail(EmailCategory.ACTIVITY)) {
            return;
        }
        String name = safeName(actor);
        String subject = localizationService.message("email.favorite.subject");
        String body = localizationService.message("email.favorite.body", name, product.getName());
        sendEmail(recipient.getEmail(), subject, body);
    }

    /**
     * Mirrors an in-app notification by email, with a link back to the page it points to,
     * so users hear about sales, shipments, offers and reviews while away from the site.
     */
    public void sendNotificationEmail(UserEntity user, NotificationType type, String title, String message, String link) {
        if (user == null || !user.wantsEmail(EmailCategory.of(type))) {
            return;
        }
        String body = localizationService.message("email.notification.body", message, absoluteUrl(link));
        sendEmail(user.getEmail(), title, body);
    }

    /** Sent for the first unread message of a conversation, not for every message. */
    public void sendNewMessageEmail(UserEntity recipient, UserEntity sender, String productName,
                                    String preview, Long conversationId) {
        if (recipient == null || !recipient.wantsEmail(EmailCategory.MESSAGES)) {
            return;
        }
        String subject = productName == null
                ? localizationService.message("email.message.subject", safeName(sender))
                : localizationService.message("email.message.subject-listing", safeName(sender), productName);
        String body = localizationService.message(
                "email.message.body", safeName(sender), preview, absoluteUrl("/messages?conversationId=" + conversationId));
        sendEmail(recipient.getEmail(), subject, body);
    }

    private String absoluteUrl(String link) {
        if (link == null || link.isBlank()) {
            return publicUrl;
        }
        return publicUrl + (link.startsWith("/") ? link : "/" + link);
    }

    private void sendEmail(String to, String subject, String body) {
        if (!enabled) {
            return;
        }
        if (to == null || to.isBlank()) {
            return;
        }
        mailDispatcher.send(from, to, subject, body);
    }

    /** Display name only: the username can be an email address and must not reach other users. */
    private String safeName(UserEntity user) {
        if (user == null || user.getDisplayName() == null || user.getDisplayName().isBlank()) {
            return localizationService.message("email.someone");
        }
        return user.getDisplayName();
    }
}
