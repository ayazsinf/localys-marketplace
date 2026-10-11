package com.localys.marketplace.service;

import com.localys.marketplace.dto.OrderCreateRequest;
import com.localys.marketplace.model.Address;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.repository.ProductRepository;
import com.localys.marketplace.repository.UserRepository;
import com.localys.marketplace.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VacationModeIntegrationTest extends IntegrationTest {

    @Autowired
    private ProductSearchService searchService;
    @Autowired
    private CartService cartService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OfferService offerService;
    @Autowired
    private PublicProfileService publicProfileService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserRepository userRepository;

    private UserEntity seller;
    private UserEntity buyer;
    private Address address;
    private Product product;
    private String keyword;

    @BeforeEach
    void setUp() {
        seller = data.user("seller");
        buyer = data.user("buyer");
        address = data.address(buyer);
        keyword = "desk" + UUID.randomUUID().toString().substring(0, 8);
        product = data.listing(data.vendor(seller), "100.00", 2);
        product.setName("Desk " + keyword);
        product.setShippingAvailable(true);
        product = productRepository.save(product);
    }

    @Test
    void awaySellersListingsAreHiddenAndCannotBeBought() {
        cartService.addItem(buyer.getId(), product.getId(), 1);
        setAway(true, "Back on Monday");

        assertThat(search()).isEmpty();
        assertThat(cartService.getActiveCart(buyer.getId()).items().get(0).availability()).isEqualTo("UNAVAILABLE");
        assertThatThrownBy(() -> cartService.addItem(buyer.getId(), product.getId(), 1))
                .hasMessage("Seller is away");
        assertThatThrownBy(() -> offerService.make(buyer.getId(), product.getId(), new BigDecimal("90")))
                .hasMessage("Seller is away");
        assertThatThrownBy(() -> orderService.createOrder(buyer.getId(), new OrderCreateRequest(
                List.of(product.getId()), address.getId(), "mondial", "card")))
                .hasMessage("Seller is away");
        assertThat(publicProfileService.get(seller.getId()).away()).isTrue();
        assertThat(publicProfileService.get(seller.getId()).awayMessage()).isEqualTo("Back on Monday");

        setAway(false, null);
        assertThat(search()).containsExactly(product.getId());
        assertThat(cartService.getActiveCart(buyer.getId()).items().get(0).availability()).isEqualTo("AVAILABLE");
        assertThat(orderService.createOrder(buyer.getId(), new OrderCreateRequest(
                List.of(product.getId()), address.getId(), "mondial", "card")).subtotal()).isEqualByComparingTo("100.00");
    }

    private void setAway(boolean away, String message) {
        UserEntity managed = userRepository.findById(seller.getId()).orElseThrow();
        managed.setVacationMode(away);
        managed.setVacationMessage(message);
        userRepository.save(managed);
    }

    private List<Long> search() {
        return searchService.search(new ProductSearchService.Criteria(
                        keyword, null, null, null, null, 0, 10, null, List.of(), null, null))
                .getContent().stream().map(Product::getId).toList();
    }
}
