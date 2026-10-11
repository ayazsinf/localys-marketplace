-- Sellers get paid through Stripe Connect; the platform keeps a commission on each item.
ALTER TABLE users ADD COLUMN IF NOT EXISTS stripe_account_id VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS stripe_payouts_enabled BOOLEAN NOT NULL DEFAULT FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_stripe_account ON users(stripe_account_id) WHERE stripe_account_id IS NOT NULL;

ALTER TABLE order_items ADD COLUMN IF NOT EXISTS commission_amount NUMERIC(12,2);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS payout_amount NUMERIC(12,2);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS transfer_id VARCHAR(100);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS paid_out_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_order_items_unpaid_payouts ON order_items(vendor_id)
    WHERE escrow_status = 'RELEASED' AND transfer_id IS NULL;

-- Sales settled before payouts existed must not be paid out again.
UPDATE order_items SET transfer_id = 'LEGACY'
WHERE escrow_status = 'RELEASED' AND transfer_id IS NULL;
