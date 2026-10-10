-- Hibernate binds @Enumerated(STRING) values as VARCHAR, which PostgreSQL refuses to
-- assign to native ENUM columns ("column is of type vendor_status but expression is of
-- type character varying"). Same fix as V15 did for carts.status.
-- Safe to re-run: converting a VARCHAR column to VARCHAR is a no-op.

ALTER TABLE addresses ALTER COLUMN type TYPE VARCHAR(20) USING type::text;

ALTER TABLE vendors ALTER COLUMN status DROP DEFAULT;
ALTER TABLE vendors ALTER COLUMN status TYPE VARCHAR(20) USING status::text;
ALTER TABLE vendors ALTER COLUMN status SET DEFAULT 'PENDING';

ALTER TABLE orders ALTER COLUMN status DROP DEFAULT;
ALTER TABLE orders ALTER COLUMN status TYPE VARCHAR(30) USING status::text;
ALTER TABLE orders ALTER COLUMN status SET DEFAULT 'PENDING';

ALTER TABLE payments ALTER COLUMN provider DROP DEFAULT;
ALTER TABLE payments ALTER COLUMN provider TYPE VARCHAR(20) USING provider::text;
ALTER TABLE payments ALTER COLUMN provider SET DEFAULT 'STRIPE';

ALTER TABLE payments ALTER COLUMN status DROP DEFAULT;
ALTER TABLE payments ALTER COLUMN status TYPE VARCHAR(30) USING status::text;
ALTER TABLE payments ALTER COLUMN status SET DEFAULT 'REQUIRES_PAYMENT';

DROP TYPE IF EXISTS address_type;
DROP TYPE IF EXISTS vendor_status;
DROP TYPE IF EXISTS cart_status;
DROP TYPE IF EXISTS order_status;
DROP TYPE IF EXISTS payment_provider;
DROP TYPE IF EXISTS payment_status;
