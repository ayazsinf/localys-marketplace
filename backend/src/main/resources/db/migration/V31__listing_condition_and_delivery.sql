-- Classifieds-style listing details: the item's condition and how it can be handed over.
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS item_condition VARCHAR(20),
    ADD COLUMN IF NOT EXISTS hand_delivery BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS shipping_available BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_products_item_condition
    ON products(item_condition);
