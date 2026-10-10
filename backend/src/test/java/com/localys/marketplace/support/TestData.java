package com.localys.marketplace.support;

import com.localys.marketplace.model.Address;
import com.localys.marketplace.model.Cart;
import com.localys.marketplace.model.CartItem;
import com.localys.marketplace.model.Order;
import com.localys.marketplace.model.Payment;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.Vendor;
import com.localys.marketplace.model.enums.AddressType;
import com.localys.marketplace.model.enums.CartStatus;
import com.localys.marketplace.model.enums.ModerationStatus;
import com.localys.marketplace.model.enums.PaymentStatus;
import com.localys.marketplace.model.enums.USER_ROLE;
import com.localys.marketplace.model.enums.VendorStatus;
import com.localys.marketplace.repository.AddressRepository;
import com.localys.marketplace.repository.CartRepository;
import com.localys.marketplace.repository.OrderRepository;
import com.localys.marketplace.repository.PaymentRepository;
import com.localys.marketplace.repository.ProductRepository;
import com.localys.marketplace.repository.UserRepository;
import com.localys.marketplace.repository.VendorRepository;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Builds fixtures with unique keys so tests never depend on each other's rows.
 */
@TestComponent
public class TestData {

    private final UserRepository userRepository;
    private final VendorRepository vendorRepository;
    private final ProductRepository productRepository;
    private final AddressRepository addressRepository;
    private final CartRepository cartRepository;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final JdbcTemplate jdbcTemplate;

    public TestData(
            UserRepository userRepository,
            VendorRepository vendorRepository,
            ProductRepository productRepository,
            AddressRepository addressRepository,
            CartRepository cartRepository,
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.userRepository = userRepository;
        this.vendorRepository = vendorRepository;
        this.productRepository = productRepository;
        this.addressRepository = addressRepository;
        this.cartRepository = cartRepository;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public UserEntity user(String name) {
        String unique = name + "-" + UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setKeycloakId(unique);
        user.setUsername(unique);
        user.setEmail(unique + "@test.local");
        user.setDisplayName(name);
        user.setPassword("{noop}unused");
        user.setRole(USER_ROLE.ROLE_USER);
        return userRepository.save(user);
    }

    public Vendor vendor(UserEntity owner) {
        Vendor vendor = new Vendor();
        vendor.setUser(owner);
        vendor.setStatus(VendorStatus.APPROVED);
        vendor.setShopName(owner.getDisplayName() + " shop");
        return vendorRepository.save(vendor);
    }

    /** An approved, active listing that can be bought. */
    public Product listing(Vendor vendor, String price, int stock) {
        Product product = new Product();
        product.setVendor(vendor);
        product.setSku("SKU-" + UUID.randomUUID());
        product.setName("Item " + product.getSku().substring(4, 12));
        product.setDescription("A test listing description");
        product.setPrice(new BigDecimal(price));
        product.setStockQty(stock);
        product.setActive(true);
        product.setModerationStatus(ModerationStatus.APPROVED);
        product.setExpiresAt(OffsetDateTime.now().plusDays(30));
        return productRepository.save(product);
    }

    public Address address(UserEntity user) {
        Address address = new Address();
        address.setUser(user);
        address.setType(AddressType.SHIPPING);
        address.setFullName(user.getDisplayName());
        address.setLine1("1 rue de Test");
        address.setCity("Paris");
        address.setPostalCode("75001");
        address.setCountry("FR");
        return addressRepository.save(address);
    }

    @Transactional
    public void addToCart(UserEntity user, Product product, int quantity) {
        Cart cart = cartRepository.findByUserIdAndStatus(user.getId(), CartStatus.ACTIVE)
                .orElseGet(() -> {
                    Cart created = new Cart();
                    created.setUser(user);
                    created.setStatus(CartStatus.ACTIVE);
                    return cartRepository.save(created);
                });
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setUnitPriceSnapshot(product.getPrice());
        item.setCurrency(product.getCurrency());
        cart.getItems().add(item);
        cartRepository.save(cart);
    }

    /** The payment record that StripePaymentService creates when a checkout session opens. */
    @Transactional
    public Payment payment(Long orderId, String sessionId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setStatus(PaymentStatus.REQUIRES_PAYMENT);
        payment.setAmount(order.getTotalAmount());
        payment.setCurrency(order.getCurrency());
        payment.setProviderSessionId(sessionId);
        payment.setIdempotencyKey("order-" + orderId + "-" + UUID.randomUUID());
        return paymentRepository.save(payment);
    }

    public int stockOf(Long productId) {
        return productRepository.findById(productId).orElseThrow().getStockQty();
    }

    public void backdateOrder(Long orderId, OffsetDateTime createdAt) {
        jdbcTemplate.update("update orders set created_at = ? where id = ?", createdAt, orderId);
    }
}
