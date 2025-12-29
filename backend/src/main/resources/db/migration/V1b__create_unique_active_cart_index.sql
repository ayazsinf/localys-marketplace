-- Create unique index after cleanup
CREATE UNIQUE INDEX IF NOT EXISTS ux_one_active_cart_per_user
    ON carts(user_id)
    WHERE status = 'ACTIVE';
