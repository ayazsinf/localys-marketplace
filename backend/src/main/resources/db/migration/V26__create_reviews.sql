-- Buyers rate the seller once per delivered order item.
CREATE TABLE IF NOT EXISTS reviews (
    id BIGSERIAL PRIMARY KEY,
    order_item_id BIGINT NOT NULL UNIQUE REFERENCES order_items(id) ON DELETE CASCADE,
    reviewer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    vendor_id BIGINT NOT NULL REFERENCES vendors(id) ON DELETE CASCADE,
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reviews_vendor_created
    ON reviews(vendor_id, created_at DESC);

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS notifications_type_check;

ALTER TABLE notifications
    ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('PRODUCT_CREATED', 'PRODUCT_PENDING_REVIEW', 'PRODUCT_APPROVED', 'PRODUCT_REJECTED',
                    'FAVORITE_ADDED', 'ORDER_PAID', 'ORDER_SOLD', 'ORDER_SHIPPED', 'ORDER_DELIVERED',
                    'REVIEW_RECEIVED'));
