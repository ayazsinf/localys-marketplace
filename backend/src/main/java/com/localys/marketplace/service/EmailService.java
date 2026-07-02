package com.localys.marketplace.service;

import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.UserEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final LocalizationService localizationService;
    private final boolean enabled;
    private final String from;

    public EmailService(
            JavaMailSender mailSender,
            LocalizationService localizationService,
            @Value("${app.mail.enabled:false}") boolean enabled,
            @Value("${app.mail.from:no-reply@localys.example}") String from
    ) {
        this.mailSender = mailSender;
        this.localizationService = localizationService;
        this.enabled = enabled;
        this.from = from;
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
        if (user == null || product == null) {
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
        if (user == null || product == null) {
            return;
        }
        String subject = localizationService.message("email.product.approved.subject");
        String body = localizationService.message("email.product.approved.body", product.getName());
        sendEmail(user.getEmail(), subject, body);
    }

    public void sendProductRejectedEmail(UserEntity user, Product product) {
        if (user == null || product == null) {
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
        if (recipient == null || actor == null || product == null) {
            return;
        }
        String name = safeName(actor);
        String subject = localizationService.message("email.favorite.subject");
        String body = localizationService.message("email.favorite.body", name, product.getName());
        sendEmail(recipient.getEmail(), subject, body);
    }

    private void sendEmail(String to, String subject, String body) {
        if (!enabled) {
            return;
        }
        if (to == null || to.isBlank()) {
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            logger.warn("Email send failed to {}: {}", to, ex.getMessage());
        }
    }

    private String safeName(UserEntity user) {
        if (user == null) {
            return "Someone";
        }
        String name = user.getDisplayName();
        if (name == null || name.isBlank()) {
            name = user.getUsername();
        }
        if (name == null || name.isBlank()) {
            name = "Someone";
        }
        return name;
    }
}
