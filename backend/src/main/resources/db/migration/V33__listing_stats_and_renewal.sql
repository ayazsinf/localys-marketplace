-- Listing statistics for sellers and "renew" to bring a listing back to the top.
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS view_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS listed_at TIMESTAMPTZ;

-- Newest-first ordering uses listed_at; until renewed it is the creation time.
UPDATE products SET listed_at = created_at WHERE listed_at IS NULL;
ALTER TABLE products ALTER COLUMN listed_at SET NOT NULL;
ALTER TABLE products ALTER COLUMN listed_at SET DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_products_listed_at ON products(listed_at DESC);
