CREATE TABLE IF NOT EXISTS favorites
(
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_favorites_user_product
    ON favorites(user_id, product_id);

CREATE INDEX IF NOT EXISTS idx_favorites_user
    ON favorites(user_id);

CREATE INDEX IF NOT EXISTS idx_favorites_product
    ON favorites(product_id);
