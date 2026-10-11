package com.localys.marketplace.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.localys.marketplace.service.MessageSafetyService.Warning.CONTACT_DETAILS;
import static com.localys.marketplace.service.MessageSafetyService.Warning.EXTERNAL_LINK;
import static com.localys.marketplace.service.MessageSafetyService.Warning.PAYMENT_OUTSIDE;
import static org.assertj.core.api.Assertions.assertThat;

class MessageSafetyServiceTest {

    private final MessageSafetyService service = new MessageSafetyService();

    @ParameterizedTest
    @ValueSource(strings = {
            "Hello, is it still available?",
            "Can you do 80 euros?",
            "I can pick it up Saturday at 14:30",
            "The size is 42, colour 2024 model",
            "Selling 3 pcs together"
    })
    void normalMessagesAreFine(String text) {
        assertThat(service.check(text)).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "I will pay by Western Union|PAYMENT_OUTSIDE",
            "Send me your IBAN please|PAYMENT_OUTSIDE",
            "My account FR76 3000 6000 0112 3456 7890 189|PAYMENT_OUTSIDE",
            "Havale yapabilirim|PAYMENT_OUTSIDE",
            "EFT ile odeyelim|PAYMENT_OUTSIDE",
            "Pay on https://secure-delivery-pay.com/abc|EXTERNAL_LINK",
            "Check www.example.com|EXTERNAL_LINK",
            "Go to paylocal.site to confirm|EXTERNAL_LINK",
            "Call me on 06 12 34 56 78|CONTACT_DETAILS",
            "+33612345678|CONTACT_DETAILS",
            "Write to me: john.doe@mail.com|CONTACT_DETAILS",
            "Add me on WhatsApp|CONTACT_DETAILS"
    })
    void suspiciousMessagesAreFlagged(String text, MessageSafetyService.Warning expected) {
        assertThat(service.check(text)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Pay by bitcoin, link: www.x.com"})
    void paymentWinsOverOtherSigns(String text) {
        assertThat(service.check(text)).isEqualTo(PAYMENT_OUTSIDE);
        assertThat(service.check("www.x.com and 0612345678")).isEqualTo(EXTERNAL_LINK);
        assertThat(service.check("mail me a@b.fr")).isEqualTo(CONTACT_DETAILS);
    }
}
