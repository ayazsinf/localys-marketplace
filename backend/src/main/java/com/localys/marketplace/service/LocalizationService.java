package com.localys.marketplace.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class LocalizationService {

    private final MessageSource messageSource;
    private final Locale defaultLocale;

    public LocalizationService(
            MessageSource messageSource,
            @Value("${app.locale.default:en}") String defaultLocale
    ) {
        this.messageSource = messageSource;
        this.defaultLocale = Locale.forLanguageTag(defaultLocale);
    }

    public String message(String key, Object... args) {
        return messageSource.getMessage(key, args, defaultLocale);
    }
}
