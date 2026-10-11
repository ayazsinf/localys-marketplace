-- Sellers can pay to feature a listing at the top of newest-first results for a few days.
ALTER TABLE products ADD COLUMN IF NOT EXISTS boosted_until TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_products_boosted_until ON products(boosted_until) WHERE boosted_until IS NOT NULL;

CREATE TABLE IF NOT EXISTS listing_boosts (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    days INT NOT NULL CHECK (days > 0),
    amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_session_id VARCHAR(200),
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_listing_boosts_session ON listing_boosts(provider_session_id)
    WHERE provider_session_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_listing_boosts_product ON listing_boosts(product_id, created_at DESC);
