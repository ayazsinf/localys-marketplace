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
    private final boolean enabled;
    private final String from;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${app.mail.enabled:false}") boolean enabled,
            @Value("${app.mail.from:no-reply@localys.example}") String from
    ) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
    }

    public void sendWelcomeEmail(UserEntity user) {
        if (user == null) {
            return;
        }
        String subject = "Welcome to Localys";
        String body = "Hosgeldiniz " + safeName(user) + ".\n\nLocalys hesabiniz hazir.";
        sendEmail(user.getEmail(), subject, body);
    }

    public void sendProductCreatedEmail(UserEntity user, Product product) {
        if (user == null || product == null) {
            return;
        }
        String subject = "Listing created";
        String body = "Your listing \"" + product.getName() + "\" is now live.";
        sendEmail(user.getEmail(), subject, body);
    }

    public void sendFavoriteAddedEmail(UserEntity recipient, UserEntity actor, Product product) {
        if (recipient == null || actor == null || product == null) {
            return;
        }
        String name = safeName(actor);
        String subject = "Favori eklendi";
        String body = name + " \"" + product.getName() + "\" urununu favorilerine ekledi.";
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
