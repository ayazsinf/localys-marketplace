package com.localys.marketplace.service;

import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.Vendor;
import com.localys.marketplace.repository.ProductRepository;
import com.localys.marketplace.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AreaSearchIntegrationTest extends IntegrationTest {

    private static final double PARIS_LAT = 48.8566;
    private static final double PARIS_LNG = 2.3522;

    @Autowired
    private ProductSearchService searchService;
    @Autowired
    private ProductRepository productRepository;

    private Vendor vendor;
    private String keyword;

    @BeforeEach
    void setUp() {
        vendor = data.vendor(data.user("seller"));
        keyword = "lamp" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void onlyListingsInsideTheRadiusMatch() {
        Product paris = listing(PARIS_LAT, PARIS_LNG);
        Product versailles = listing(48.8049, 2.1204); // about 19 km away
        Product lyon = listing(45.7640, 4.8357);       // about 390 km away
        Product noLocation = listing(null, null);

        assertThat(ids(null)).containsExactlyInAnyOrder(paris.getId(), versailles.getId(), lyon.getId(), noLocation.getId());
        assertThat(ids(ProductSearchService.Area.of(PARIS_LAT, PARIS_LNG, 10.0))).containsExactly(paris.getId());
        assertThat(ids(ProductSearchService.Area.of(PARIS_LAT, PARIS_LNG, 30.0)))
                .containsExactlyInAnyOrder(paris.getId(), versailles.getId());
        // The radius is capped at 200 km, so Lyon stays out.
        assertThat(ids(ProductSearchService.Area.of(PARIS_LAT, PARIS_LNG, 5000.0)))
                .containsExactlyInAnyOrder(paris.getId(), versailles.getId());
    }

    @Test
    void invalidPointsMeanNoAreaFilter() {
        assertThat(ProductSearchService.Area.of(null, PARIS_LNG, 10.0)).isNull();
        assertThat(ProductSearchService.Area.of(95.0, PARIS_LNG, 10.0)).isNull();
        assertThat(ProductSearchService.Area.of(PARIS_LAT, PARIS_LNG, null).radiusKm()).isEqualTo(10);
        assertThat(ProductSearchService.Area.of(PARIS_LAT, PARIS_LNG, 0.1).radiusKm()).isEqualTo(1);
    }

    private List<Long> ids(ProductSearchService.Area area) {
        return searchService.search(new ProductSearchService.Criteria(
                        keyword, null, null, null, null, 0, 50, null, List.of(), null, area))
                .getContent().stream().map(Product::getId).toList();
    }

    private Product listing(Double latitude, Double longitude) {
        Product product = data.listing(vendor, "20.00", 1);
        product.setName("Lamp " + keyword);
        product.setLatitude(latitude);
        product.setLongitude(longitude);
        return productRepository.save(product);
    }
}
