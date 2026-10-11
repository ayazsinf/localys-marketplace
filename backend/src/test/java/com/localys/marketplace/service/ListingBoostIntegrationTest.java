package com.localys.marketplace.service;

import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.Vendor;
import com.localys.marketplace.repository.ProductRepository;
import com.localys.marketplace.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListingBoostIntegrationTest extends IntegrationTest {

    @MockBean
    private PaymentGateway paymentGateway;

    @Autowired
    private ListingBoostService boostService;
    @Autowired
    private ProductSearchService searchService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserEntity seller;
    private Vendor vendor;
    private String keyword;

    @BeforeEach
    void setUp() {
        seller = data.user("seller");
        vendor = data.vendor(seller);
        keyword = "sofa" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void paidBoostsPutTheListingFirstAndStack() {
        Product older = listing(OffsetDateTime.now().minusDays(3));
        Product newer = listing(OffsetDateTime.now());
        assertThat(newestFirst()).containsExactly(newer.getId(), older.getId());

        String session = "cs_" + UUID.randomUUID();
        when(paymentGateway.createCheckout(anyString(), any(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(new PaymentGateway.CheckoutSession(session, "https://checkout.stripe.test/boost"));
        assertThat(boostService.startCheckout(seller.getId(), older.getId())).isEqualTo("https://checkout.stripe.test/boost");
        verify(paymentGateway).createCheckout(anyString(), any(), eq("EUR"), anyString(), anyString(),
                eq(Map.of("kind", "BOOST", "boostId", boostIdFrom())), anyString());
        // Not featured until paid.
        assertThat(newestFirst()).containsExactly(newer.getId(), older.getId());

        assertThat(boostService.onSessionPaid(session)).isTrue();
        assertThat(boostService.onSessionPaid(session)).isTrue();

        OffsetDateTime boostedUntil = productRepository.findById(older.getId()).orElseThrow().getBoostedUntil();
        assertThat(boostedUntil).isCloseTo(OffsetDateTime.now().plusDays(7), within(5, MINUTES));
        assertThat(newestFirst()).containsExactly(older.getId(), newer.getId());
        Page<Product> page = searchService.search(criteria());
        assertThat(page.getTotalElements()).isEqualTo(2);

        // Buying again extends the running feature.
        String second = "cs_" + UUID.randomUUID();
        when(paymentGateway.createCheckout(anyString(), any(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(new PaymentGateway.CheckoutSession(second, "https://checkout.stripe.test/boost2"));
        boostService.startCheckout(seller.getId(), older.getId());
        boostService.onSessionPaid(second);
        assertThat(productRepository.findById(older.getId()).orElseThrow().getBoostedUntil())
                .isCloseTo(boostedUntil.plusDays(7), within(1, MINUTES));
    }

    @Test
    void onlyTheSellersOnlineListingsCanBeBoosted() {
        Product product = listing(OffsetDateTime.now());
        assertThatThrownBy(() -> boostService.startCheckout(data.user("other").getId(), product.getId()))
                .hasMessage("Product not found");
        product.setActive(false);
        productRepository.save(product);
        assertThatThrownBy(() -> boostService.startCheckout(seller.getId(), product.getId()))
                .hasMessage("Only online listings can be boosted");
        assertThat(boostService.onSessionPaid("cs_unknown")).isFalse();
        assertThat(boostService.onSessionClosedUnpaid("cs_unknown")).isFalse();
    }

    /** The boost this seller created last. */
    private String boostIdFrom() {
        return String.valueOf(jdbcTemplate.queryForObject(
                "select max(id) from listing_boosts where user_id = ?", Long.class, seller.getId()));
    }

    private Product listing(OffsetDateTime listedAt) {
        Product product = data.listing(vendor, "100.00", 1);
        product.setName("Sofa " + keyword);
        product.setListedAt(listedAt);
        return productRepository.save(product);
    }

    private ProductSearchService.Criteria criteria() {
        return new ProductSearchService.Criteria(keyword, null, null, null, "NEWEST", 0, 10, null, List.of(), null, null);
    }

    private List<Long> newestFirst() {
        return searchService.search(criteria()).getContent().stream().map(Product::getId).toList();
    }
}
