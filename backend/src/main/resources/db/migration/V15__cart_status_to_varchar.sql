DROP INDEX IF EXISTS ux_one_active_cart_per_user;
DROP INDEX IF EXISTS idx_carts_status;

ALTER TABLE carts
    ALTER COLUMN status DROP DEFAULT;

ALTER TABLE carts
    ALTER COLUMN status TYPE VARCHAR(20) USING status::text;

ALTER TABLE carts
    ALTER COLUMN status SET DEFAULT 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_carts_status ON carts(status);
CREATE UNIQUE INDEX IF NOT EXISTS ux_one_active_cart_per_user
    ON carts(user_id)
    WHERE status = 'ACTIVE';
