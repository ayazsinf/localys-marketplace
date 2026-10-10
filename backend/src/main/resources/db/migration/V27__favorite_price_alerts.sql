-- Price each user last saw for a favorited listing; a price drop below it triggers an alert.
ALTER TABLE favorites
    ADD COLUMN IF NOT EXISTS last_seen_price NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS last_seen_currency VARCHAR(3);

UPDATE favorites f
SET last_seen_price = p.price,
    last_seen_currency = p.currency
FROM products p
WHERE p.id = f.product_id
  AND f.last_seen_price IS NULL;

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS notifications_type_check;

ALTER TABLE notifications
    ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('PRODUCT_CREATED', 'PRODUCT_PENDING_REVIEW', 'PRODUCT_APPROVED', 'PRODUCT_REJECTED',
                    'FAVORITE_ADDED', 'ORDER_PAID', 'ORDER_SOLD', 'ORDER_SHIPPED', 'ORDER_DELIVERED',
                    'REVIEW_RECEIVED', 'PRICE_DROP'));
