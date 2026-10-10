package com.localys.marketplace.service;

import com.localys.marketplace.dto.CartDto;
import com.localys.marketplace.dto.OfferDto;
import com.localys.marketplace.dto.OrderCreateRequest;
import com.localys.marketplace.dto.OrderResponse;
import com.localys.marketplace.model.Address;
import com.localys.marketplace.model.Offer;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.enums.NotificationType;
import com.localys.marketplace.model.enums.OfferStatus;
import com.localys.marketplace.repository.NotificationRepository;
import com.localys.marketplace.repository.OfferRepository;
import com.localys.marketplace.repository.ProductRepository;
import com.localys.marketplace.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfferIntegrationTest extends IntegrationTest {

    @Autowired
    private OfferService offerService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private CartService cartService;
    @Autowired
    private OfferRepository offerRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    private UserEntity seller;
    private UserEntity buyer;
    private Address address;
    private Product product;

    @BeforeEach
    void setUp() {
        seller = data.user("seller");
        product = data.listing(data.vendor(seller), "100.00", 2);
        buyer = data.user("buyer");
        address = data.address(buyer);
    }

    @Test
    void negotiationRules() {
        assertThatThrownBy(() -> offerService.make(seller.getId(), product.getId(), amount("50")))
                .hasMessage("Cannot make an offer on own product");
        assertThatThrownBy(() -> offerService.make(buyer.getId(), product.getId(), amount("100")))
                .hasMessage("Offer must be below the listing price");
        assertThatThrownBy(() -> offerService.make(buyer.getId(), product.getId(), amount("0")))
                .hasMessage("Offer amount must be positive");

        OfferDto offer = offerService.make(buyer.getId(), product.getId(), amount("70"));
        assertThat(offer.status()).isEqualTo("PENDING");
        assertThat(notifications(seller, NotificationType.OFFER_RECEIVED)).isEqualTo(1);
        assertThatThrownBy(() -> offerService.make(buyer.getId(), product.getId(), amount("75")))
                .hasMessage("Offer already open for this listing");

        // Only the seller answers a pending offer.
        assertThatThrownBy(() -> offerService.accept(buyer.getId(), offer.id()))
                .hasMessage("Offer cannot be answered");
        UserEntity stranger = data.user("stranger");
        assertThatThrownBy(() -> offerService.accept(stranger.getId(), offer.id()))
                .hasMessage("Offer not found");
        assertThatThrownBy(() -> offerService.counter(seller.getId(), offer.id(), amount("60")))
                .hasMessage("Counter offer must be between the offer and the listing price");

        OfferDto countered = offerService.counter(seller.getId(), offer.id(), amount("85"));
        assertThat(countered.status()).isEqualTo("COUNTERED");
        assertThat(countered.amount()).isEqualByComparingTo("85.00");
        assertThat(notifications(buyer, NotificationType.OFFER_COUNTERED)).isEqualTo(1);
        // Now it is the buyer's turn.
        assertThatThrownBy(() -> offerService.accept(seller.getId(), offer.id()))
                .hasMessage("Offer cannot be answered");

        OfferDto declined = offerService.decline(buyer.getId(), offer.id());
        assertThat(declined.status()).isEqualTo("DECLINED");
        assertThat(notifications(seller, NotificationType.OFFER_DECLINED)).isEqualTo(1);

        // Once closed, a new offer can be made.
        assertThat(offerService.make(buyer.getId(), product.getId(), amount("80")).status()).isEqualTo("PENDING");
        assertThat(offerService.listForSeller(seller.getId())).filteredOn(OfferDto::awaitingMe).hasSize(1);
    }

    @Test
    void acceptedOfferIsBoughtAtItsPriceOnce() {
        OfferDto offer = offerService.make(buyer.getId(), product.getId(), amount("70"));
        assertThatThrownBy(() -> offerService.addToCart(buyer.getId(), offer.id()))
                .hasMessage("Offer is not accepted or has expired");

        OfferDto accepted = offerService.accept(seller.getId(), offer.id());
        assertThat(accepted.expiresAt()).isAfter(OffsetDateTime.now().plusHours(47));
        assertThat(notifications(buyer, NotificationType.OFFER_ACCEPTED)).isEqualTo(1);

        CartDto cart = offerService.addToCart(buyer.getId(), offer.id());
        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).unitPrice()).isEqualByComparingTo("70.00");
        assertThat(cart.items().get(0).offerApplied()).isTrue();
        // The negotiated price is for one unit.
        cart = cartService.addItem(buyer.getId(), product.getId(), 1);
        assertThat(cart.items().get(0).quantity()).isEqualTo(1);

        OrderResponse order = orderService.createOrder(buyer.getId(), new OrderCreateRequest(
                List.of(product.getId()), address.getId(), "mondial", "card"));
        assertThat(order.subtotal()).isEqualByComparingTo("70.00");

        // An abandoned payment puts the offer back in the cart.
        orderService.cancelUnpaid(order.id());
        assertThat(cartService.getActiveCart(buyer.getId()).items().get(0).unitPrice()).isEqualByComparingTo("70.00");

        OrderResponse retry = orderService.createOrder(buyer.getId(), new OrderCreateRequest(
                List.of(product.getId()), address.getId(), "mondial", "card"));
        orderService.markPaid(retry.id());
        assertThat(offerRepository.findById(offer.id()).map(Offer::getStatus)).contains(OfferStatus.PURCHASED);
        assertThatThrownBy(() -> offerService.addToCart(buyer.getId(), offer.id()))
                .hasMessage("Offer is not accepted or has expired");
    }

    @Test
    void cartUsesTheCurrentListingPriceWithoutAnOffer() {
        cartService.addItem(buyer.getId(), product.getId(), 1);
        Product managed = productRepository.findById(product.getId()).orElseThrow();
        managed.setPrice(new BigDecimal("90.00"));
        productRepository.save(managed);

        assertThat(cartService.getActiveCart(buyer.getId()).items().get(0).unitPrice()).isEqualByComparingTo("90.00");
        OrderResponse order = orderService.createOrder(buyer.getId(), new OrderCreateRequest(
                List.of(product.getId()), address.getId(), "mondial", "card"));
        assertThat(order.subtotal()).isEqualByComparingTo("90.00");
    }

    @Test
    void expiredOrWithdrawnOffersFallBackToTheListingPrice() {
        OfferDto offer = offerService.make(buyer.getId(), product.getId(), amount("60"));
        offerService.accept(seller.getId(), offer.id());
        offerService.addToCart(buyer.getId(), offer.id());

        Offer stored = offerRepository.findById(offer.id()).orElseThrow();
        stored.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        offerRepository.save(stored);
        offerService.expireAcceptedOffers();

        assertThat(offerRepository.findById(offer.id()).map(Offer::getStatus)).contains(OfferStatus.EXPIRED);
        CartDto cart = cartService.getActiveCart(buyer.getId());
        assertThat(cart.items().get(0).unitPrice()).isEqualByComparingTo("100.00");
        assertThat(cart.items().get(0).offerApplied()).isFalse();

        OfferDto second = offerService.make(buyer.getId(), product.getId(), amount("65"));
        offerService.withdraw(buyer.getId(), second.id());
        assertThat(offerRepository.findById(second.id()).map(Offer::getStatus)).contains(OfferStatus.WITHDRAWN);
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private long notifications(UserEntity user, NotificationType type) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(notification -> notification.getType() == type)
                .count();
    }
}
