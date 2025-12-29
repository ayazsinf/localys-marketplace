-- =========================
-- Marketplace V1 schema
-- PostgreSQL 16
-- =========================

-- 1) Enum'lar (type)
DO
$$
BEGIN
CREATE TYPE address_type AS ENUM ('SHIPPING', 'BILLING');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO
$$
BEGIN
CREATE TYPE vendor_status AS ENUM ('PENDING', 'APPROVED', 'SUSPENDED');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO
$$
BEGIN
CREATE TYPE cart_status AS ENUM ('ACTIVE', 'CHECKED_OUT', 'ABANDONED');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO
$$
BEGIN
CREATE TYPE order_status AS ENUM ('CREATED', 'PAYMENT_PENDING', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'REFUNDED');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO
$$
BEGIN
CREATE TYPE payment_provider AS ENUM ('STRIPE', 'ADYEN', 'PAYPAL');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO
$$
BEGIN
CREATE TYPE payment_status AS ENUM ('REQUIRES_PAYMENT', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'REFUNDED');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- 2) KullanÄ±cÄ± (Keycloak ile baÄŸ)
CREATE TABLE IF NOT EXISTS users
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    keycloak_id
    VARCHAR
(
    64
) NOT NULL UNIQUE, -- jwt.sub
    username VARCHAR
(
    150
),
    email VARCHAR
(
    254
),
    display_name VARCHAR
(
    200
),
    phone VARCHAR
(
    40
),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
)
    );

-- Backfill missing keycloak_id for existing databases
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS keycloak_id VARCHAR(64);

UPDATE users
SET keycloak_id = 'legacy_' || id
WHERE keycloak_id IS NULL;

ALTER TABLE users
    ALTER COLUMN keycloak_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_keycloak_id ON users(keycloak_id);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);

-- 3) Adresler
CREATE TABLE IF NOT EXISTS addresses
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    user_id
    BIGINT
    NOT
    NULL
    REFERENCES
    users
(
    id
) ON DELETE CASCADE,
    type address_type NOT NULL,
    label VARCHAR
(
    80
), -- Home/Office
    full_name VARCHAR
(
    200
),
    phone VARCHAR
(
    40
),
    line1 VARCHAR
(
    200
) NOT NULL,
    line2 VARCHAR
(
    200
),
    city VARCHAR
(
    120
) NOT NULL,
    postal_code VARCHAR
(
    20
) NOT NULL,
    country VARCHAR
(
    2
) NOT NULL DEFAULT 'FR',
    is_default_shipping BOOLEAN NOT NULL DEFAULT FALSE,
    is_default_billing BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
)
    );

CREATE INDEX IF NOT EXISTS idx_addresses_user ON addresses(user_id);

-- 4) Vendor profili (satÄ±cÄ±)
CREATE TABLE IF NOT EXISTS vendors
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    user_id
    BIGINT
    NOT
    NULL
    UNIQUE
    REFERENCES
    users
(
    id
) ON DELETE CASCADE,
    status vendor_status NOT NULL DEFAULT 'PENDING',
    shop_name VARCHAR
(
    160
) NOT NULL,
    legal_name VARCHAR
(
    200
),
    vat_number VARCHAR
(
    80
),
    iban VARCHAR
(
    64
), -- V2: encrypt / vault
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
)
    );

CREATE INDEX IF NOT EXISTS idx_vendors_status ON vendors(status);

-- 5) Kategoriler (opsiyonel ama iyi temel)
CREATE TABLE IF NOT EXISTS categories
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    name
    VARCHAR
(
    120
) NOT NULL,
    parent_id BIGINT REFERENCES categories
(
    id
) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
)
    );

CREATE UNIQUE INDEX IF NOT EXISTS ux_categories_name_parent
    ON categories(name, COALESCE (parent_id, 0));

-- 6) ÃœrÃ¼nler
CREATE TABLE IF NOT EXISTS products
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    vendor_id
    BIGINT
    NOT
    NULL
    REFERENCES
    vendors
(
    id
) ON DELETE RESTRICT,
    category_id BIGINT REFERENCES categories
(
    id
)
  ON DELETE SET NULL,
    sku VARCHAR
(
    64
) NOT NULL UNIQUE,
    name VARCHAR
(
    200
) NOT NULL,
    description TEXT,
    brand VARCHAR
(
    80
),
    price NUMERIC
(
    12,
    2
) NOT NULL,
    currency CHAR
(
    3
) NOT NULL DEFAULT 'EUR',
    stock_qty INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
)
    );

CREATE INDEX IF NOT EXISTS idx_products_vendor ON products(vendor_id);
CREATE INDEX IF NOT EXISTS idx_products_category ON products(category_id);
CREATE INDEX IF NOT EXISTS idx_products_active ON products(active);

CREATE TABLE IF NOT EXISTS product_images
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    product_id
    BIGINT
    NOT
    NULL
    REFERENCES
    products
(
    id
) ON DELETE CASCADE,
    url TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0
    );

CREATE INDEX IF NOT EXISTS idx_product_images_product ON product_images(product_id);

-- 7) Sepet
CREATE TABLE IF NOT EXISTS carts
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    user_id
    BIGINT
    NOT
    NULL
    REFERENCES
    users
(
    id
) ON DELETE CASCADE,
    status cart_status NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
)
    );

CREATE INDEX IF NOT EXISTS idx_carts_user ON carts(user_id);
CREATE INDEX IF NOT EXISTS idx_carts_status ON carts(status);

-- KullanÄ±cÄ±nÄ±n aynÄ± anda 1 aktif sepeti olsun (partial unique index)

CREATE TABLE IF NOT EXISTS cart_items
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    cart_id
    BIGINT
    NOT
    NULL
    REFERENCES
    carts
(
    id
) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products
(
    id
)
  ON DELETE RESTRICT,
    quantity INT NOT NULL CHECK
(
    quantity >
    0
),
    unit_price_snapshot NUMERIC
(
    12,
    2
) NOT NULL,
    currency CHAR
(
    3
) NOT NULL DEFAULT 'EUR',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
)
    );

CREATE INDEX IF NOT EXISTS idx_cart_items_cart ON cart_items(cart_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_cart_item_unique_product
    ON cart_items(cart_id, product_id);

-- 8) SipariÅŸ
CREATE TABLE IF NOT EXISTS orders
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    user_id
    BIGINT
    NOT
    NULL
    REFERENCES
    users
(
    id
) ON DELETE RESTRICT,
    status order_status NOT NULL DEFAULT 'CREATED',
    shipping_address_id BIGINT REFERENCES addresses
(
    id
)
  ON DELETE SET NULL,
    billing_address_id BIGINT REFERENCES addresses
(
    id
)
  ON DELETE SET NULL,
    total_amount NUMERIC
(
    12,
    2
) NOT NULL DEFAULT 0,
    currency CHAR
(
    3
) NOT NULL DEFAULT 'EUR',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
)
    );

CREATE INDEX IF NOT EXISTS idx_orders_user ON orders(user_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);

CREATE TABLE IF NOT EXISTS order_items
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    order_id
    BIGINT
    NOT
    NULL
    REFERENCES
    orders
(
    id
) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products
(
    id
)
  ON DELETE RESTRICT,
    vendor_id BIGINT NOT NULL REFERENCES vendors
(
    id
)
  ON DELETE RESTRICT,
    product_name_snapshot VARCHAR
(
    200
) NOT NULL,
    unit_price_snapshot NUMERIC
(
    12,
    2
) NOT NULL,
    quantity INT NOT NULL CHECK
(
    quantity >
    0
),
    line_total NUMERIC
(
    12,
    2
) NOT NULL,
    currency CHAR
(
    3
) NOT NULL DEFAULT 'EUR'
    );

CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items(order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_vendor ON order_items(vendor_id);

-- 9) Ã–deme
CREATE TABLE IF NOT EXISTS payments
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    order_id
    BIGINT
    NOT
    NULL
    REFERENCES
    orders
(
    id
) ON DELETE RESTRICT,
    provider payment_provider NOT NULL DEFAULT 'STRIPE',
    status payment_status NOT NULL DEFAULT 'REQUIRES_PAYMENT',
    amount NUMERIC
(
    12,
    2
) NOT NULL,
    currency CHAR
(
    3
) NOT NULL DEFAULT 'EUR',
    provider_session_id VARCHAR
(
    200
), -- Stripe Checkout Session id
    provider_payment_intent_id VARCHAR
(
    200
), -- Stripe PaymentIntent id (ops)
    idempotency_key VARCHAR
(
    80
) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
)
    );

-- 1 order iÃ§in birden fazla deneme istersen UNIQUE kaldÄ±rÄ±lÄ±r (ÅŸimdilik V1 sade: 1 Ã¶deme kaydÄ±)
CREATE UNIQUE INDEX IF NOT EXISTS ux_payments_order ON payments(order_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_payments_idempotency ON payments(idempotency_key);

-- =========================
-- Seed data (minimal)
-- =========================

-- Kategoriler
INSERT INTO categories (name, parent_id, created_at)
VALUES ('Carpets', NULL, NOW()),
       ('Vintage', (SELECT id FROM categories WHERE name = 'Carpets' AND parent_id IS NULL), NOW()),
       ('Modern', (SELECT id FROM categories WHERE name = 'Carpets' AND parent_id IS NULL), NOW()) ON CONFLICT DO NOTHING;
-- Demo Ã¼rÃ¼nler
INSERT INTO products (vendor_id, category_id, sku, name, description, brand, price, currency, stock_qty, active)
SELECT v.id,
       (SELECT id FROM categories WHERE name = 'Vintage' LIMIT 1),
  'CARPET-001',
  'Kilim Vintage Rouge 120x180',
  'Kilim vintage, motifs traditionnels.',
  'Localys',
  129.50,
  'EUR',
  7,
  TRUE
FROM vendors v
WHERE v.shop_name='Localys Shop'
ON CONFLICT (sku) DO NOTHING;

INSERT INTO products (vendor_id, category_id, sku, name, description, brand, price, currency, stock_qty, active)
SELECT v.id,
       (SELECT id FROM categories WHERE name = 'Modern' LIMIT 1),
  'CARPET-002',
  'Tapis Moderne Gris 200x290',
  'Tapis moderne, facile Ã  nettoyer.',
  'Localys',
  249.00,
  'EUR',
  4,
  TRUE
FROM vendors v
WHERE v.shop_name='Localys Shop'
ON CONFLICT (sku) DO NOTHING;

INSERT INTO product_images (product_id, url, sort_order)
SELECT p.id, 'https://picsum.photos/seed/' || p.sku || '/800/500', 0
FROM products p
WHERE p.sku IN ('CARPET-001', 'CARPET-002') ON CONFLICT DO NOTHING;



