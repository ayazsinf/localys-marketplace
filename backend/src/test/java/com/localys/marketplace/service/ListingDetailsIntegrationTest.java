package com.localys.marketplace.service;

import com.localys.marketplace.dto.OrderCreateRequest;
import com.localys.marketplace.model.Address;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.ProductImage;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.Vendor;
import com.localys.marketplace.model.enums.ItemCondition;
import com.localys.marketplace.repository.ProductRepository;
import com.localys.marketplace.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListingDetailsIntegrationTest extends IntegrationTest {

    @Autowired
    private ProductSearchService searchService;
    @Autowired
    private ProductService productService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private CartService cartService;
    @Autowired
    private ProductRepository productRepository;

    private Vendor vendor;
    private String keyword;

    @BeforeEach
    void setUp() {
        vendor = data.vendor(data.user("seller"));
        keyword = "bike" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void filtersByConditionAndDelivery() {
        Product newShipped = listing(ItemCondition.NEW, false, true);
        Product goodHand = listing(ItemCondition.GOOD, true, false);
        Product goodBoth = listing(ItemCondition.GOOD, true, true);
        listing(null, false, true);

        assertThat(ids(List.of(ItemCondition.GOOD), null)).containsExactlyInAnyOrder(goodHand.getId(), goodBoth.getId());
        assertThat(ids(List.of(), ProductSearchService.Delivery.HAND)).containsExactlyInAnyOrder(goodHand.getId(), goodBoth.getId());
        assertThat(ids(List.of(ItemCondition.NEW, ItemCondition.GOOD), ProductSearchService.Delivery.SHIPPING))
                .containsExactlyInAnyOrder(newShipped.getId(), goodBoth.getId());
        assertThat(ids(List.of(), null)).hasSize(4);
    }

    @Test
    void handDeliveryOnlyListingsCannotBeBoughtOnline() {
        Product handOnly = listing(ItemCondition.LIKE_NEW, true, false);
        UserEntity buyer = data.user("buyer");
        Address address = data.address(buyer);

        assertThatThrownBy(() -> cartService.addItem(buyer.getId(), handOnly.getId(), 1))
                .hasMessage("Product is hand delivery only");

        // Switched to hand delivery after the buyer put it in the cart.
        Product shippable = listing(ItemCondition.GOOD, true, true);
        cartService.addItem(buyer.getId(), shippable.getId(), 1);
        shippable.setShippingAvailable(false);
        productRepository.save(shippable);
        assertThat(cartService.getActiveCart(buyer.getId()).items().get(0).availability()).isEqualTo("UNAVAILABLE");
        assertThatThrownBy(() -> orderService.createOrder(buyer.getId(), new OrderCreateRequest(
                List.of(shippable.getId()), address.getId(), "mondial", "card")))
                .hasMessage("Product is hand delivery only");
    }

    @Test
    void sellerRemovesPhotosAndOrderIsCompacted() {
        Product product = listing(ItemCondition.GOOD, false, true);
        inTx(() -> {
            Product managed = productRepository.findById(product.getId()).orElseThrow();
            for (int i = 0; i < 3; i++) {
                ProductImage image = new ProductImage();
                image.setProduct(managed);
                image.setUrl("/uploads/test/" + i + ".jpg");
                image.setSortOrder(i);
                managed.getImages().add(image);
            }
            return productRepository.save(managed);
        });

        List<String> remaining = productService.removeImageForVendor(product.getId(), vendor, "/uploads/test/0.jpg");

        assertThat(remaining).containsExactly("/uploads/test/1.jpg", "/uploads/test/2.jpg");
        assertThatThrownBy(() -> productService.removeImageForVendor(product.getId(), vendor, "/uploads/test/0.jpg"))
                .hasMessage("Image not found");
        Vendor other = data.vendor(data.user("other"));
        assertThatThrownBy(() -> productService.removeImageForVendor(product.getId(), other, "/uploads/test/1.jpg"))
                .hasMessage("Product not found");
    }

    private List<Long> ids(List<ItemCondition> conditions, ProductSearchService.Delivery delivery) {
        return searchService.search(new ProductSearchService.Criteria(
                        keyword, null, null, null, null, 0, 50, null, conditions, delivery, null))
                .getContent().stream().map(Product::getId).toList();
    }

    private Product listing(ItemCondition condition, boolean hand, boolean shipping) {
        Product product = data.listing(vendor, "50.00", 1);
        product.setName("Bike " + keyword);
        product.setCondition(condition);
        product.setHandDelivery(hand);
        product.setShippingAvailable(shipping);
        return productRepository.save(product);
    }
}
