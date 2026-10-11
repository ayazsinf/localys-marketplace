package com.localys.marketplace.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * A seller's earnings.
 *
 * @param pending   held under buyer protection (after commission)
 * @param available released but not yet paid out (waiting for payout setup or a retry)
 * @param paidOut   transferred to the seller's account
 */
public record WalletDto(
        boolean accountConnected,
        boolean payoutsEnabled,
        BigDecimal commissionRate,
        String currency,
        BigDecimal pending,
        BigDecimal available,
        BigDecimal paidOut,
        List<WalletEntryDto> entries
) {
    /**
     * @param state PENDING, DISPUTED, AVAILABLE, PAID_OUT or REFUNDED
     */
    public record WalletEntryDto(
            Long orderId,
            Long orderItemId,
            String productName,
            BigDecimal lineTotal,
            BigDecimal commission,
            BigDecimal payout,
            String currency,
            String state,
            OffsetDateTime soldAt,
            OffsetDateTime paidOutAt
    ) {
    }
}
