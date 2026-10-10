package com.localys.marketplace.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends a fully built email on a background thread, so a slow or unreachable SMTP server
 * never holds up the request (or transaction) that triggered it.
 */
@Service
public class MailDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(MailDispatcher.class);

    private final JavaMailSender mailSender;

    public MailDispatcher(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void send(String from, String to, String subject, String body) {
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
}
