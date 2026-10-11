package com.localys.marketplace.service;

import com.localys.marketplace.dto.WalletDto;
import com.localys.marketplace.model.OrderItem;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.enums.EscrowStatus;
import com.localys.marketplace.repository.OrderItemRepository;
import com.localys.marketplace.repository.PaymentRepository;
import com.localys.marketplace.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Pays sellers through Stripe Connect. When buyer protection releases an item, the platform
 * keeps its commission and transfers the rest to the seller's account. Sellers who have not
 * finished payout setup yet are paid as soon as they do.
 */
@Service
public class SellerPayoutService {

    private static final Logger logger = LoggerFactory.getLogger(SellerPayoutService.class);

    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final PaymentGateway paymentGateway;
    private final TransactionTemplate transactionTemplate;
    private final BigDecimal commissionRate;
    private final String sellerCountry;
    private final String publicUrl;

    public SellerPayoutService(
            OrderItemRepository orderItemRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            PaymentGateway paymentGateway,
            TransactionTemplate transactionTemplate,
            @Value("${app.marketplace.commission-rate:0.05}") BigDecimal commissionRate,
            @Value("${app.marketplace.seller-country:FR}") String sellerCountry,
            @Value("${app.public-url:http://localhost:4200}") String publicUrl
    ) {
        if (commissionRate.signum() < 0 || commissionRate.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalStateException("app.marketplace.commission-rate must be between 0 and 1");
        }
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.paymentGateway = paymentGateway;
        this.transactionTemplate = transactionTemplate;
        this.commissionRate = commissionRate;
        this.sellerCountry = sellerCountry;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    /** The commission on an item: the amount fixed at release, or what it would be now. */
    public BigDecimal commissionFor(OrderItem item) {
        if (item.getCommissionAmount() != null) {
            return item.getCommissionAmount();
        }
        return item.getLineTotal().multiply(commissionRate).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal payoutFor(OrderItem item) {
        return item.getPayoutAmount() != null ? item.getPayoutAmount() : item.getLineTotal().subtract(commissionFor(item));
    }

    /**
     * Called when an item's money is released: fixes the commission and pays the seller if
     * they can receive money. A failed transfer is retried later.
     */
    public void settle(OrderItem item) {
        item.setCommissionAmount(commissionFor(item));
        item.setPayoutAmount(item.getLineTotal().subtract(item.getCommissionAmount()));
        tryTransfer(item);
    }

    /** Retries payouts that could not be made at release time. */
    @Scheduled(cron = "${app.payouts.retry-cron:0 30 * * * *}")
    public void payPending() {
        payItems(orderItemRepository.findIdsAwaitingPayout());
    }

    @Transactional(readOnly = true)
    public WalletDto wallet(Long sellerUserId) {
        UserEntity seller = userRepository.findById(sellerUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        List<WalletDto.WalletEntryDto> entries = orderItemRepository.findProtectedSales(sellerUserId).stream()
                .map(this::toEntry)
                .toList();
        String currency = entries.isEmpty() ? "EUR" : entries.get(0).currency();
        return new WalletDto(
                seller.getStripeAccountId() != null,
                seller.isStripePayoutsEnabled(),
                commissionRate,
                currency,
                sum(entries, "PENDING", "DISPUTED"),
                sum(entries, "AVAILABLE"),
                sum(entries, "PAID_OUT"),
                entries
        );
    }

    /**
     * Opens the seller's payout account if needed and returns the Stripe page where they
     * enter their identity and bank details.
     */
    @Transactional
    public String startOnboarding(Long sellerUserId) {
        UserEntity seller = userRepository.findById(sellerUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (seller.getStripeAccountId() == null) {
            seller.setStripeAccountId(paymentGateway.createSellerAccount(seller.getEmail(), sellerCountry));
            userRepository.save(seller);
        }
        return paymentGateway.sellerOnboardingLink(seller.getStripeAccountId(),
                publicUrl + "/wallet?onboarding=refresh", publicUrl + "/wallet?onboarding=done");
    }

    /** Asks Stripe whether onboarding is finished (the seller just came back from it). */
    public void refreshAccount(Long sellerUserId) {
        UserEntity seller = userRepository.findById(sellerUserId).orElse(null);
        if (seller == null || seller.getStripeAccountId() == null || seller.isStripePayoutsEnabled()) {
            return;
        }
        onAccountUpdated(seller.getStripeAccountId(), paymentGateway.payoutsEnabled(seller.getStripeAccountId()));
    }

    /** Records the account's state and pays what the seller was owed once they can be paid. */
    public void onAccountUpdated(String accountId, boolean payoutsEnabled) {
        Long sellerId = transactionTemplate.execute(status -> userRepository.findByStripeAccountId(accountId)
                .map(user -> {
                    user.setStripePayoutsEnabled(payoutsEnabled);
                    return userRepository.save(user).getId();
                })
                .orElse(null));
        if (sellerId != null && payoutsEnabled) {
            payItems(orderItemRepository.findIdsAwaitingPayoutForSeller(sellerId));
        }
    }

    private void payItems(List<Long> itemIds) {
        for (Long itemId : itemIds) {
            transactionTemplate.executeWithoutResult(status -> orderItemRepository.findByIdForUpdate(itemId)
                    .filter(item -> item.getEscrowStatus() == EscrowStatus.RELEASED && item.getTransferId() == null)
                    .ifPresent(item -> {
                        if (item.getPayoutAmount() == null) {
                            settle(item);
                        } else {
                            tryTransfer(item);
                        }
                        orderItemRepository.save(item);
                    }));
        }
    }

    private void tryTransfer(OrderItem item) {
        UserEntity seller = item.getVendor() != null ? item.getVendor().getUser() : null;
        if (seller == null || seller.getStripeAccountId() == null || !seller.isStripePayoutsEnabled()
                || item.getPayoutAmount().signum() <= 0) {
            return;
        }
        String paymentIntentId = paymentRepository.findByOrder_Id(item.getOrder().getId())
                .map(payment -> payment.getProviderPaymentIntentId())
                .orElse(null);
        try {
            String transferId = paymentGateway.transferToSeller(seller.getStripeAccountId(), item.getPayoutAmount(),
                    item.getCurrency(), paymentIntentId, "order-" + item.getOrder().getId(),
                    "payout-order-item-" + item.getId());
            item.setTransferId(transferId);
            item.setPaidOutAt(OffsetDateTime.now());
        } catch (RuntimeException ex) {
            // The money stays available to the seller; the retry job tries again.
            logger.warn("Payout of order item {} failed: {}", item.getId(), ex.getMessage());
        }
    }

    private WalletDto.WalletEntryDto toEntry(OrderItem item) {
        String state = switch (item.getEscrowStatus()) {
            case HELD -> "PENDING";
            case DISPUTED -> "DISPUTED";
            case REFUNDED -> "REFUNDED";
            case RELEASED -> item.getTransferId() != null ? "PAID_OUT" : "AVAILABLE";
        };
        return new WalletDto.WalletEntryDto(
                item.getOrder().getId(),
                item.getId(),
                item.getProductNameSnapshot(),
                item.getLineTotal(),
                commissionFor(item),
                payoutFor(item),
                item.getCurrency(),
                state,
                item.getOrder().getCreatedAt(),
                item.getPaidOutAt()
        );
    }

    private BigDecimal sum(List<WalletDto.WalletEntryDto> entries, String... states) {
        List<String> wanted = List.of(states);
        return entries.stream()
                .filter(entry -> wanted.contains(entry.state()))
                .map(WalletDto.WalletEntryDto::payout)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
