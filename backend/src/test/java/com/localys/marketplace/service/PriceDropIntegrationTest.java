package com.localys.marketplace.service;

import com.localys.marketplace.model.Notification;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.Vendor;
import com.localys.marketplace.model.enums.ModerationStatus;
import com.localys.marketplace.model.enums.NotificationType;
import com.localys.marketplace.repository.NotificationRepository;
import com.localys.marketplace.repository.ProductRepository;
import com.localys.marketplace.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PriceDropIntegrationTest extends IntegrationTest {

    @Autowired
    private ProductService productService;
    @Autowired
    private FavoriteService favoriteService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    private Vendor vendor;
    private UserEntity admin;
    private UserEntity fan;
    private Product product;

    @BeforeEach
    void setUp() {
        UserEntity seller = data.user("seller");
        vendor = data.vendor(seller);
        admin = data.user("admin");
        fan = data.user("fan");
        product = data.listing(vendor, "100.00", 1);
        favoriteService.addFavorite(fan.getId(), product.getId());
    }

    @Test
    void alertsOnceWhenTheLowerPriceIsApproved() {
        updatePrice("80.00");
        // A price change goes back to moderation; nobody is told before it is visible again.
        assertThat(productRepository.findById(product.getId()).orElseThrow().getModerationStatus())
                .isEqualTo(ModerationStatus.PENDING);
        assertThat(priceDrops()).isEmpty();

        productService.approveProduct(product.getId(), admin);
        productService.approveProduct(product.getId(), admin);

        List<Notification> drops = priceDrops();
        assertThat(drops).hasSize(1);
        assertThat(drops.get(0).getMessage()).contains("\"80.00 EUR\"").contains("\"100.00 EUR\"");
        assertThat(drops.get(0).getLink()).isEqualTo("/products/" + product.getId());
    }

    @Test
    void priceIncreaseResetsTheBaseline() {
        updatePrice("120.00");
        productService.approveProduct(product.getId(), admin);
        assertThat(priceDrops()).isEmpty();

        // 110 is cheaper than the last price the user saw (120), though above the original 100.
        updatePrice("110.00");
        productService.approveProduct(product.getId(), admin);
        assertThat(priceDrops()).hasSize(1);
    }

    @Test
    void noAlertAfterUnfavoriting() {
        favoriteService.removeFavorite(fan.getId(), product.getId());

        updatePrice("50.00");
        productService.approveProduct(product.getId(), admin);

        assertThat(priceDrops()).isEmpty();
    }

    private void updatePrice(String price) {
        Product current = productRepository.findById(product.getId()).orElseThrow();
        Product details = new Product();
        details.setName(current.getName());
        details.setDescription(current.getDescription());
        details.setBrand(current.getBrand());
        details.setPrice(new BigDecimal(price));
        details.setStockQty(current.getStockQty());
        details.setActive(true);
        details.setCountry(current.getCountry());
        details.setCurrency(current.getCurrency());
        productService.updateProductForVendor(product.getId(), details, vendor);
    }

    private List<Notification> priceDrops() {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(fan.getId()).stream()
                .filter(notification -> notification.getType() == NotificationType.PRICE_DROP)
                .toList();
    }
}
