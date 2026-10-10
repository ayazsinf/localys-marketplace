-- Buyers make price offers on listings; the seller accepts, declines or counters.
CREATE TABLE IF NOT EXISTS offers (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    buyer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    seller_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- A buyer has at most one open negotiation per listing.
CREATE UNIQUE INDEX IF NOT EXISTS ux_offers_open_per_buyer
    ON offers(product_id, buyer_id)
    WHERE status IN ('PENDING', 'COUNTERED', 'ACCEPTED');

CREATE INDEX IF NOT EXISTS idx_offers_buyer ON offers(buyer_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_offers_seller ON offers(seller_id, updated_at DESC);

-- The accepted offer a cart line / order line is priced with.
ALTER TABLE cart_items ADD COLUMN IF NOT EXISTS offer_id BIGINT REFERENCES offers(id) ON DELETE SET NULL;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS offer_id BIGINT REFERENCES offers(id) ON DELETE SET NULL;

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS notifications_type_check;

ALTER TABLE notifications
    ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('PRODUCT_CREATED', 'PRODUCT_PENDING_REVIEW', 'PRODUCT_APPROVED', 'PRODUCT_REJECTED',
                    'FAVORITE_ADDED', 'ORDER_PAID', 'ORDER_SOLD', 'ORDER_SHIPPED', 'ORDER_DELIVERED',
                    'REVIEW_RECEIVED', 'PRICE_DROP', 'LISTING_REPORTED', 'SAVED_SEARCH_MATCH',
                    'OFFER_RECEIVED', 'OFFER_COUNTERED', 'OFFER_ACCEPTED', 'OFFER_DECLINED'));
