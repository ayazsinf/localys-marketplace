-- Buyer protection: the money for each sold item is held until the buyer confirms receipt,
-- and can be disputed and refunded meanwhile.
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS escrow_status VARCHAR(20);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS escrow_released_at TIMESTAMPTZ;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS refunded_at TIMESTAMPTZ;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS refund_amount NUMERIC(12,2);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS refund_id VARCHAR(100);

-- When the payment arrived; the seller has a deadline from then to ship.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ;

-- Items paid before this feature were already settled outside the platform's escrow.
UPDATE order_items oi SET escrow_status = 'RELEASED', escrow_released_at = NOW()
FROM orders o
WHERE oi.order_id = o.id AND oi.escrow_status IS NULL AND o.status IN ('PAID', 'SHIPPED', 'DELIVERED');

CREATE INDEX IF NOT EXISTS idx_order_items_escrow ON order_items(escrow_status) WHERE escrow_status IN ('HELD', 'DISPUTED');

CREATE TABLE IF NOT EXISTS disputes (
    id BIGSERIAL PRIMARY KEY,
    order_item_id BIGINT NOT NULL REFERENCES order_items(id) ON DELETE CASCADE,
    buyer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    seller_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason VARCHAR(30) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    seller_response VARCHAR(2000),
    resolution_note VARCHAR(1000),
    resolved_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- At most one open dispute per item.
CREATE UNIQUE INDEX IF NOT EXISTS ux_disputes_open_per_item
    ON disputes(order_item_id) WHERE status IN ('OPEN', 'CONTESTED');
CREATE INDEX IF NOT EXISTS idx_disputes_buyer ON disputes(buyer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_disputes_seller ON disputes(seller_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_disputes_status ON disputes(status);

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS notifications_type_check;

ALTER TABLE notifications
    ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('PRODUCT_CREATED', 'PRODUCT_PENDING_REVIEW', 'PRODUCT_APPROVED', 'PRODUCT_REJECTED',
                    'FAVORITE_ADDED', 'ORDER_PAID', 'ORDER_SOLD', 'ORDER_SHIPPED', 'ORDER_DELIVERED',
                    'REVIEW_RECEIVED', 'PRICE_DROP', 'LISTING_REPORTED', 'SAVED_SEARCH_MATCH',
                    'OFFER_RECEIVED', 'OFFER_COUNTERED', 'OFFER_ACCEPTED', 'OFFER_DECLINED',
                    'DISPUTE_OPENED', 'DISPUTE_CLOSED', 'ORDER_REFUNDED', 'PAYMENT_RELEASED'));
