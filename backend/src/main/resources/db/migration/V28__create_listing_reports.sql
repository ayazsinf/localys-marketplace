-- Users flag listings that break the rules; admins dismiss the report or remove the listing.
CREATE TABLE IF NOT EXISTS listing_reports (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    reporter_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason VARCHAR(30) NOT NULL,
    details VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    resolved_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- A user can only have one open report per listing.
CREATE UNIQUE INDEX IF NOT EXISTS ux_listing_reports_open
    ON listing_reports(product_id, reporter_id)
    WHERE status = 'OPEN';

CREATE INDEX IF NOT EXISTS idx_listing_reports_status_created
    ON listing_reports(status, created_at DESC);

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS notifications_type_check;

ALTER TABLE notifications
    ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('PRODUCT_CREATED', 'PRODUCT_PENDING_REVIEW', 'PRODUCT_APPROVED', 'PRODUCT_REJECTED',
                    'FAVORITE_ADDED', 'ORDER_PAID', 'ORDER_SOLD', 'ORDER_SHIPPED', 'ORDER_DELIVERED',
                    'REVIEW_RECEIVED', 'PRICE_DROP', 'LISTING_REPORTED'));
