-- Searches users saved from the home page; the alert job tells them about new matches.
CREATE TABLE IF NOT EXISTS saved_searches (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    query VARCHAR(200),
    category_id BIGINT REFERENCES categories(id) ON DELETE SET NULL,
    min_price NUMERIC(12,2),
    max_price NUMERIC(12,2),
    alerts_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_checked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_saved_searches_user
    ON saved_searches(user_id);

CREATE INDEX IF NOT EXISTS idx_saved_searches_alerts
    ON saved_searches(alerts_enabled)
    WHERE alerts_enabled = TRUE;

-- New matches are listings approved after the last check.
CREATE INDEX IF NOT EXISTS idx_products_reviewed_at
    ON products(reviewed_at);

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS notifications_type_check;

ALTER TABLE notifications
    ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('PRODUCT_CREATED', 'PRODUCT_PENDING_REVIEW', 'PRODUCT_APPROVED', 'PRODUCT_REJECTED',
                    'FAVORITE_ADDED', 'ORDER_PAID', 'ORDER_SOLD', 'ORDER_SHIPPED', 'ORDER_DELIVERED',
                    'REVIEW_RECEIVED', 'PRICE_DROP', 'LISTING_REPORTED', 'SAVED_SEARCH_MATCH'));
