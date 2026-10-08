ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS stock_deducted BOOLEAN NOT NULL DEFAULT FALSE;

-- Orders that already reached a terminal paid/shipped/delivered state before this
-- migration had their stock deducted at creation time under the old behavior.
UPDATE orders
SET stock_deducted = TRUE
WHERE status IN ('PAID', 'SHIPPED', 'DELIVERED', 'REFUNDED');
