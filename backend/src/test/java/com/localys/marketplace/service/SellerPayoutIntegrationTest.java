package com.localys.marketplace.service;

import com.localys.marketplace.dto.OrderCreateRequest;
import com.localys.marketplace.dto.WalletDto;
import com.localys.marketplace.model.Address;
import com.localys.marketplace.model.OrderItem;
import com.localys.marketplace.model.Payment;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.Vendor;
import com.localys.marketplace.model.enums.PaymentStatus;
import com.localys.marketplace.repository.OrderItemRepository;
import com.localys.marketplace.repository.PaymentRepository;
import com.localys.marketplace.repository.UserRepository;
import com.localys.marketplace.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SellerPayoutIntegrationTest extends IntegrationTest {

    @MockBean
    private PaymentGateway paymentGateway;

    @Autowired
    private SellerPayoutService payoutService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private UserRepository userRepository;

    private UserEntity seller;
    private Vendor vendor;
    private UserEntity buyer;
    private Address address;
    private String accountId;

    @BeforeEach
    void setUp() {
        seller = data.user("seller");
        vendor = data.vendor(seller);
        buyer = data.user("buyer");
        address = data.address(buyer);
        accountId = "acct_" + UUID.randomUUID();
        when(paymentGateway.transferToSeller(anyString(), any(), anyString(), any(), anyString(), anyString()))
                .thenReturn("tr_test");
    }

    @Test
    void sellersArePaidOnceTheyFinishPayoutSetup() {
        Long orderId = deliveredOrder(listing("40.00"));
        OrderItem item = item(orderId);
        // 5% commission; nothing can be transferred before payout setup.
        assertThat(item.getCommissionAmount()).isEqualByComparingTo("2.00");
        assertThat(item.getPayoutAmount()).isEqualByComparingTo("38.00");
        assertThat(item.getTransferId()).isNull();
        WalletDto wallet = payoutService.wallet(seller.getId());
        assertThat(wallet.available()).isEqualByComparingTo("38.00");
        assertThat(wallet.payoutsEnabled()).isFalse();
        assertThat(orderService.listSales(seller.getId()).get(0).items().get(0).payoutAmount()).isEqualByComparingTo("38.00");
        assertThat(orderService.getForBuyer(buyer.getId(), orderId).items().get(0).payoutAmount()).isNull();

        when(paymentGateway.createSellerAccount(anyString(), eq("FR"))).thenReturn(accountId);
        when(paymentGateway.sellerOnboardingLink(eq(accountId), anyString(), anyString())).thenReturn("https://connect.stripe.test/onboard");
        assertThat(payoutService.startOnboarding(seller.getId())).isEqualTo("https://connect.stripe.test/onboard");
        payoutService.startOnboarding(seller.getId());
        verify(paymentGateway, times(1)).createSellerAccount(anyString(), anyString());

        // Coming back from Stripe with onboarding done pays what is owed.
        when(paymentGateway.payoutsEnabled(accountId)).thenReturn(true);
        payoutService.refreshAccount(seller.getId());

        verify(paymentGateway).transferToSeller(eq(accountId), argThat(amount -> amount.compareTo(new BigDecimal("38.00")) == 0),
                eq("EUR"), eq("pi_" + orderId), eq("order-" + orderId), eq("payout-order-item-" + item.getId()));
        assertThat(item(orderId).getTransferId()).isEqualTo("tr_test");
        WalletDto paid = payoutService.wallet(seller.getId());
        assertThat(paid.paidOut()).isEqualByComparingTo("38.00");
        assertThat(paid.available()).isZero();
        assertThat(paid.entries()).singleElement().satisfies(entry -> assertThat(entry.state()).isEqualTo("PAID_OUT"));
    }

    @Test
    void connectedSellersArePaidOnReleaseAndFailedTransfersAreRetried() {
        connect();
        when(paymentGateway.transferToSeller(anyString(), any(), anyString(), any(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("Payout failed: insufficient funds"))
                .thenReturn("tr_retry");

        Long orderId = deliveredOrder(listing("19.99"));
        assertThat(item(orderId).getTransferId()).isNull();
        assertThat(item(orderId).getCommissionAmount()).isEqualByComparingTo("1.00");
        assertThat(payoutService.wallet(seller.getId()).available()).isEqualByComparingTo("18.99");

        payoutService.payPending();

        assertThat(item(orderId).getTransferId()).isEqualTo("tr_retry");
        payoutService.payPending();
        verify(paymentGateway, times(2)).transferToSeller(anyString(), any(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void heldItemsShowAsPendingAndAreNotTransferred() {
        connect();
        Long orderId = paidOrder(listing("50.00"));

        WalletDto wallet = payoutService.wallet(seller.getId());
        assertThat(wallet.pending()).isEqualByComparingTo("47.50");
        assertThat(wallet.available()).isZero();
        payoutService.payPending();
        verify(paymentGateway, never()).transferToSeller(anyString(), any(), anyString(), any(), anyString(), anyString());
        assertThat(item(orderId).getCommissionAmount()).isNull();
    }

    private void connect() {
        UserEntity managed = userRepository.findById(seller.getId()).orElseThrow();
        managed.setStripeAccountId(accountId);
        managed.setStripePayoutsEnabled(true);
        userRepository.save(managed);
    }

    private Product listing(String price) {
        return data.listing(vendor, price, 1);
    }

    private Long paidOrder(Product product) {
        data.addToCart(buyer, product, 1);
        Long orderId = orderService.createOrder(buyer.getId(), new OrderCreateRequest(
                List.of(product.getId()), address.getId(), "mondial", "card")).id();
        Payment payment = data.payment(orderId, "cs_" + UUID.randomUUID());
        payment.setProviderPaymentIntentId("pi_" + orderId);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        paymentRepository.save(payment);
        orderService.markPaid(orderId);
        return orderId;
    }

    private Long deliveredOrder(Product product) {
        Long orderId = paidOrder(product);
        orderService.shipItems(seller.getId(), orderId, "TRACK");
        orderService.confirmDelivery(buyer.getId(), orderId);
        return orderId;
    }

    private OrderItem item(Long orderId) {
        return orderItemRepository.findAll().stream()
                .filter(item -> item.getOrder().getId().equals(orderId))
                .findFirst()
                .orElseThrow();
    }
}
