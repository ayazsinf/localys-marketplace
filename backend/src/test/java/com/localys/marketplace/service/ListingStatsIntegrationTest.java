package com.localys.marketplace.service;

import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.Vendor;
import com.localys.marketplace.repository.ProductRepository;
import com.localys.marketplace.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListingStatsIntegrationTest extends IntegrationTest {

    @Autowired
    private ListingStatsService statsService;
    @Autowired
    private ProductService productService;
    @Autowired
    private FavoriteService favoriteService;
    @Autowired
    private OfferService offerService;
    @Autowired
    private ProductSearchService searchService;
    @Autowired
    private ProductRepository productRepository;

    private Vendor vendor;
    private Product product;

    @BeforeEach
    void setUp() {
        vendor = data.vendor(data.user("seller"));
        product = data.listing(vendor, "40.00", 1);
    }

    @Test
    void countsViewsFavoritesAndOpenOffers() {
        UserEntity fan = data.user("fan");
        favoriteService.addFavorite(fan.getId(), product.getId());
        offerService.make(fan.getId(), product.getId(), new BigDecimal("30"));
        statsService.recordView(product.getId());
        statsService.recordView(product.getId());

        Map<Long, ListingStatsService.Stats> stats = statsService.statsFor(List.of(product.getId()));

        assertThat(productRepository.findById(product.getId()).orElseThrow().getViewCount()).isEqualTo(2);
        assertThat(stats.get(product.getId()).favorites()).isEqualTo(1);
        assertThat(stats.get(product.getId()).openOffers()).isEqualTo(1);
        assertThat(stats.get(product.getId()).conversations()).isZero();
    }

    @Test
    void renewMovesListingToTheTopOnceAWeek() {
        String keyword = "lamp" + UUID.randomUUID().toString().substring(0, 8);
        Product older = named(product, keyword, OffsetDateTime.now().minusDays(8));
        Product newer = named(data.listing(vendor, "20.00", 1), keyword, OffsetDateTime.now().minusDays(1));
        assertThat(newestFirst(keyword)).containsExactly(newer.getId(), older.getId());

        productService.renewForVendor(older.getId(), vendor);

        assertThat(newestFirst(keyword)).containsExactly(older.getId(), newer.getId());
        assertThatThrownBy(() -> productService.renewForVendor(older.getId(), vendor))
                .hasMessage("Listing was renewed recently");
        Vendor other = data.vendor(data.user("other"));
        assertThatThrownBy(() -> productService.renewForVendor(newer.getId(), other))
                .hasMessage("Product not found");
    }

    private Product named(Product listing, String keyword, OffsetDateTime listedAt) {
        listing.setName("Lamp " + keyword);
        listing.setListedAt(listedAt);
        return productRepository.save(listing);
    }

    private List<Long> newestFirst(String keyword) {
        return searchService.search(new ProductSearchService.Criteria(
                        keyword, null, null, null, "NEWEST", 0, 10, null, List.of(), null))
                .getContent().stream().map(Product::getId).toList();
    }
}
