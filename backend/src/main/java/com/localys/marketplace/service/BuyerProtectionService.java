package com.localys.marketplace.service;

import com.localys.marketplace.dto.DisputeDto;
import com.localys.marketplace.model.Dispute;
import com.localys.marketplace.model.Order;
import com.localys.marketplace.model.OrderItem;
import com.localys.marketplace.model.Payment;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.enums.DisputeReason;
import com.localys.marketplace.model.enums.DisputeStatus;
import com.localys.marketplace.model.enums.EscrowStatus;
import com.localys.marketplace.model.enums.OrderStatus;
import com.localys.marketplace.model.enums.PaymentStatus;
import com.localys.marketplace.repository.DisputeRepository;
import com.localys.marketplace.repository.OrderItemRepository;
import com.localys.marketplace.repository.OrderRepository;
import com.localys.marketplace.repository.PaymentRepository;
import com.localys.marketplace.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Buyer protection, like a classifieds "secure payment": the money for each item stays with
 * the platform until the buyer confirms receiving it. Meanwhile the buyer can report a
 * problem; the seller refunds or contests, and an admin settles contested cases.
 * <ul>
 *     <li>Not shipped within {@link #SHIPPING_DEADLINE} of payment: refunded automatically.</li>
 *     <li>Shipped but neither confirmed nor disputed within {@link #AUTO_RELEASE_AFTER_SHIPPING}:
 *     treated as received and released.</li>
 * </ul>
 */
@Service
public class BuyerProtectionService {

    private static final Logger logger = LoggerFactory.getLogger(BuyerProtectionService.class);

    static final Duration SHIPPING_DEADLINE = Duration.ofDays(7);
    static final Duration AUTO_RELEASE_AFTER_SHIPPING = Duration.ofDays(14);
    static final int MAX_TEXT_LENGTH = 2000;
    static final int MAX_NOTE_LENGTH = 1000;

    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final DisputeRepository disputeRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final PaymentGateway paymentGateway;
    private final NotificationService notificationService;
    private final AdminRecipientService adminRecipientService;
    private final TransactionTemplate transactionTemplate;
    private final SellerPayoutService sellerPayoutService;

    public BuyerProtectionService(
            OrderItemRepository orderItemRepository,
            OrderRepository orderRepository,
            DisputeRepository disputeRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            PaymentGateway paymentGateway,
            NotificationService notificationService,
            AdminRecipientService adminRecipientService,
            TransactionTemplate transactionTemplate,
            SellerPayoutService sellerPayoutService
    ) {
        this.sellerPayoutService = sellerPayoutService;
        this.orderItemRepository = orderItemRepository;
        this.orderRepository = orderRepository;
        this.disputeRepository = disputeRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.paymentGateway = paymentGateway;
        this.notificationService = notificationService;
        this.adminRecipientService = adminRecipientService;
        this.transactionTemplate = transactionTemplate;
    }

    /** Called inside the payment transaction: the money of every item is now held. */
    public void holdPayment(Order order) {
        order.setPaidAt(OffsetDateTime.now());
        for (OrderItem item : order.getItems()) {
            if (item.getEscrowStatus() == null) {
                item.setEscrowStatus(EscrowStatus.HELD);
            }
        }
    }

    /** Called when the buyer confirms receiving these items: their money goes to the seller. */
    public void releaseConfirmed(List<OrderItem> items) {
        items.stream()
                .filter(item -> item.getEscrowStatus() == EscrowStatus.HELD)
                .forEach(this::release);
    }

    @Transactional
    public DisputeDto openDispute(Long buyerId, Long orderId, Long orderItemId, String rawReason, String rawDescription) {
        DisputeReason reason = DisputeReason.from(rawReason)
                .orElseThrow(() -> new IllegalArgumentException("Invalid dispute reason"));
        String description = trimToNull(rawDescription);
        if (description == null || description.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Dispute description is required (max 2000 characters)");
        }
        orderRepository.findByIdForUpdate(orderId)
                .filter(order -> order.getUser().getId().equals(buyerId))
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        OrderItem item = orderItemRepository.findByIdForUpdate(orderItemId)
                .filter(found -> found.getOrder().getId().equals(orderId))
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if (item.getEscrowStatus() != EscrowStatus.HELD) {
            throw new IllegalArgumentException("Dispute not allowed for this item");
        }
        UserEntity seller = item.getVendor().getUser();

        Dispute dispute = new Dispute();
        dispute.setOrderItem(item);
        dispute.setBuyer(item.getOrder().getUser());
        dispute.setSeller(seller);
        dispute.setReason(reason);
        dispute.setDescription(description);
        dispute.setStatus(DisputeStatus.OPEN);
        Dispute saved = disputeRepository.save(dispute);
        item.setEscrowStatus(EscrowStatus.DISPUTED);
        orderItemRepository.save(item);
        notificationService.createDisputeOpenedNotification(seller, item.getProductNameSnapshot());
        return toDto(saved);
    }

    /** The buyer no longer wants to dispute; the money is held again until they confirm. */
    @Transactional
    public DisputeDto withdraw(Long buyerId, Long disputeId) {
        Dispute dispute = activeDispute(disputeId, buyerId, true);
        dispute.setStatus(DisputeStatus.WITHDRAWN);
        dispute.setResolvedAt(OffsetDateTime.now());
        dispute.getOrderItem().setEscrowStatus(EscrowStatus.HELD);
        orderItemRepository.save(dispute.getOrderItem());
        return toDto(disputeRepository.save(dispute));
    }

    /** The seller agrees with the buyer and refunds the item. */
    @Transactional
    public DisputeDto refundBySeller(Long sellerId, Long disputeId) {
        Dispute dispute = activeDispute(disputeId, sellerId, false);
        refund(dispute.getOrderItem());
        close(dispute, DisputeStatus.REFUNDED, null, null);
        notificationService.createDisputeClosedNotification(dispute.getBuyer(),
                dispute.getOrderItem().getProductNameSnapshot(), true, "/orders/" + dispute.getOrderItem().getOrder().getId());
        return toDto(dispute);
    }

    /** The seller disagrees; the case goes to an admin. */
    @Transactional
    public DisputeDto contest(Long sellerId, Long disputeId, String rawResponse) {
        String response = trimToNull(rawResponse);
        if (response == null || response.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Dispute response is required (max 2000 characters)");
        }
        Dispute dispute = activeDispute(disputeId, sellerId, false);
        if (dispute.getStatus() != DisputeStatus.OPEN) {
            throw new IllegalArgumentException("Dispute already contested");
        }
        dispute.setStatus(DisputeStatus.CONTESTED);
        dispute.setSellerResponse(response);
        disputeRepository.save(dispute);
        for (UserEntity admin : adminRecipientService.findAdmins()) {
            notificationService.createDisputeContestedNotification(admin, dispute.getOrderItem().getProductNameSnapshot());
        }
        return toDto(dispute);
    }

    /** An admin settles an open or contested dispute: refund the buyer or pay the seller. */
    @Transactional
    public DisputeDto resolve(Long adminId, Long disputeId, String rawOutcome, String rawNote) {
        String outcome = rawOutcome == null ? "" : rawOutcome.trim().toUpperCase(Locale.ROOT);
        if (!outcome.equals("REFUND") && !outcome.equals("RELEASE")) {
            throw new IllegalArgumentException("Invalid dispute outcome");
        }
        String note = trimToNull(rawNote);
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("Dispute note is too long");
        }
        Dispute dispute = disputeRepository.findByIdForUpdate(disputeId)
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found"));
        if (!DisputeStatus.ACTIVE.contains(dispute.getStatus())) {
            throw new IllegalArgumentException("Dispute already closed");
        }
        UserEntity admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        OrderItem item = orderItemRepository.findByIdForUpdate(dispute.getOrderItem().getId()).orElseThrow();
        boolean refunded = outcome.equals("REFUND");
        if (refunded) {
            refund(item);
        } else {
            release(item);
        }
        close(dispute, refunded ? DisputeStatus.REFUNDED : DisputeStatus.RELEASED, admin, note);
        String product = item.getProductNameSnapshot();
        notificationService.createDisputeClosedNotification(dispute.getBuyer(), product, refunded, "/orders/" + item.getOrder().getId());
        notificationService.createDisputeClosedNotification(dispute.getSeller(), product, refunded, "/orders?tab=sales");
        return toDto(dispute);
    }

    @Transactional(readOnly = true)
    public List<DisputeDto> listForBuyer(Long buyerId) {
        return disputeRepository.findByBuyer_IdOrderByCreatedAtDesc(buyerId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<DisputeDto> listForSeller(Long sellerId) {
        return disputeRepository.findBySeller_IdOrderByCreatedAtDesc(sellerId).stream().map(this::toDto).toList();
    }

    /** {@code status} ACTIVE (default: open and contested) or ALL. */
    @Transactional(readOnly = true)
    public List<DisputeDto> listForAdmin(String status) {
        List<Dispute> disputes = "ALL".equalsIgnoreCase(status)
                ? disputeRepository.findAllByOrderByCreatedAtDesc()
                : disputeRepository.findByStatusInOrderByCreatedAtAsc(DisputeStatus.ACTIVE);
        return disputes.stream().map(this::toDto).toList();
    }

    /** Refunds unshipped items past the deadline and releases shipped items nobody confirmed. */
    @Scheduled(cron = "${app.orders.protection-cron:0 15 * * * *}")
    public void processDeadlines() {
        OffsetDateTime now = OffsetDateTime.now();
        for (Long itemId : orderItemRepository.findHeldUnshippedPaidBefore(now.minus(SHIPPING_DEADLINE))) {
            runForItem(itemId, item -> {
                if (item.getEscrowStatus() == EscrowStatus.HELD && item.getShippedAt() == null) {
                    refund(item);
                }
            });
        }
        for (Long itemId : orderItemRepository.findHeldShippedBefore(now.minus(AUTO_RELEASE_AFTER_SHIPPING))) {
            runForItem(itemId, item -> {
                if (item.getEscrowStatus() == EscrowStatus.HELD && item.getDeliveredAt() == null) {
                    item.setDeliveredAt(OffsetDateTime.now());
                    release(item);
                    updateDeliveredStatus(item.getOrder());
                }
            });
        }
    }

    private void runForItem(Long itemId, Consumer<OrderItem> action) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                // Lock the order first, like every other order change, then the item.
                orderItemRepository.findById(itemId)
                        .flatMap(found -> orderRepository.findByIdForUpdate(found.getOrder().getId()))
                        .flatMap(order -> orderItemRepository.findByIdForUpdate(itemId))
                        .ifPresent(action);
            });
        } catch (RuntimeException ex) {
            logger.warn("Buyer protection deadline failed for order item {}: {}", itemId, ex.getMessage());
        }
    }

    private void release(OrderItem item) {
        item.setEscrowStatus(EscrowStatus.RELEASED);
        item.setEscrowReleasedAt(OffsetDateTime.now());
        sellerPayoutService.settle(item);
        orderItemRepository.save(item);
        if (item.getVendor() != null && item.getVendor().getUser() != null) {
            notificationService.createPaymentReleasedNotification(
                    item.getVendor().getUser(), item.getProductNameSnapshot(), item.getPayoutAmount(), item.getCurrency());
        }
    }

    /**
     * Gives the item's price back to the buyer, plus the shipping fee when it is the last item
     * of the order still to be settled that way (nothing of the order was then sent).
     */
    private void refund(OrderItem item) {
        Order order = item.getOrder();
        Payment payment = paymentRepository.findByOrder_Id(order.getId())
                .filter(found -> found.getStatus() == PaymentStatus.SUCCEEDED || found.getStatus() == PaymentStatus.REFUNDED)
                .filter(found -> found.getProviderPaymentIntentId() != null)
                .orElseThrow(() -> new IllegalStateException("Payment cannot be refunded automatically"));
        boolean lastOpenItem = order.getItems().stream()
                .filter(other -> !other.getId().equals(item.getId()))
                .allMatch(other -> other.getEscrowStatus() == EscrowStatus.REFUNDED);
        BigDecimal amount = item.getLineTotal();
        if (lastOpenItem && order.getShippingPrice() != null) {
            amount = amount.add(order.getShippingPrice());
        }
        String refundId = paymentGateway.refund(payment.getProviderPaymentIntentId(), amount, item.getCurrency(),
                "refund-order-item-" + item.getId());

        item.setEscrowStatus(EscrowStatus.REFUNDED);
        item.setRefundAmount(amount);
        item.setRefundedAt(OffsetDateTime.now());
        item.setRefundId(refundId);
        orderItemRepository.save(item);
        if (lastOpenItem) {
            order.setStatus(OrderStatus.REFUNDED);
            payment.setStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(payment);
        } else {
            updateShippedStatus(order);
            updateDeliveredStatus(order);
        }
        orderRepository.save(order);
        notificationService.createOrderRefundedNotification(order.getUser(), order.getId(),
                item.getProductNameSnapshot(), amount, item.getCurrency());
    }

    /** A refunded item needs no shipping, so the rest of the order may now count as shipped. */
    private void updateShippedStatus(Order order) {
        if (order.getStatus() == OrderStatus.PAID && order.getItems().stream()
                .allMatch(item -> item.getShippedAt() != null || item.getEscrowStatus() == EscrowStatus.REFUNDED)) {
            order.setStatus(OrderStatus.SHIPPED);
        }
    }

    private void updateDeliveredStatus(Order order) {
        if (order.getStatus() == OrderStatus.SHIPPED && order.getItems().stream()
                .allMatch(item -> item.getDeliveredAt() != null || item.getEscrowStatus() == EscrowStatus.REFUNDED)) {
            order.setStatus(OrderStatus.DELIVERED);
        }
        orderRepository.save(order);
    }

    private Dispute activeDispute(Long disputeId, Long userId, boolean asBuyer) {
        Dispute dispute = disputeRepository.findByIdForUpdate(disputeId)
                .filter(found -> (asBuyer ? found.getBuyer() : found.getSeller()).getId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found"));
        if (!DisputeStatus.ACTIVE.contains(dispute.getStatus())) {
            throw new IllegalArgumentException("Dispute already closed");
        }
        // Lock the order like every other change to it.
        orderRepository.findByIdForUpdate(dispute.getOrderItem().getOrder().getId());
        return dispute;
    }

    private void close(Dispute dispute, DisputeStatus status, UserEntity admin, String note) {
        dispute.setStatus(status);
        dispute.setResolvedBy(admin);
        dispute.setResolutionNote(note);
        dispute.setResolvedAt(OffsetDateTime.now());
        disputeRepository.save(dispute);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private DisputeDto toDto(Dispute dispute) {
        OrderItem item = dispute.getOrderItem();
        return new DisputeDto(
                dispute.getId(),
                item.getOrder().getId(),
                item.getId(),
                item.getProduct().getId(),
                item.getProductNameSnapshot(),
                item.getLineTotal(),
                item.getCurrency(),
                dispute.getReason().name(),
                dispute.getDescription(),
                dispute.getStatus().name(),
                dispute.getSellerResponse(),
                dispute.getResolutionNote(),
                dispute.getBuyer().getDisplayName(),
                dispute.getSeller().getDisplayName(),
                dispute.getCreatedAt(),
                dispute.getResolvedAt()
        );
    }
}
