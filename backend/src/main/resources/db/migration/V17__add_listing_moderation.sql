ALTER TABLE products
    ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS moderation_reason VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reviewed_by BIGINT;

UPDATE products
SET moderation_status = 'APPROVED'
WHERE moderation_status IS NULL;

ALTER TABLE products
    ALTER COLUMN moderation_status SET DEFAULT 'PENDING',
    ALTER COLUMN moderation_status SET NOT NULL;

ALTER TABLE products
    DROP CONSTRAINT IF EXISTS products_moderation_status_check;

ALTER TABLE products
    ADD CONSTRAINT products_moderation_status_check
    CHECK (moderation_status IN ('PENDING', 'APPROVED', 'REJECTED'));

DO
$$
BEGIN
    ALTER TABLE products
        ADD CONSTRAINT fk_products_reviewed_by
        FOREIGN KEY (reviewed_by) REFERENCES users(id) ON DELETE SET NULL;
EXCEPTION
    WHEN duplicate_object THEN NULL;
END
$$;

CREATE INDEX IF NOT EXISTS idx_products_moderation_status
    ON products(moderation_status);

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS notifications_type_check;

ALTER TABLE notifications
    ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('PRODUCT_CREATED', 'PRODUCT_APPROVED', 'PRODUCT_REJECTED', 'FAVORITE_ADDED'));
