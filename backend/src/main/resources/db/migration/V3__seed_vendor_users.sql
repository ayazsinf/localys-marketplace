-- Seed vendor demo users + vendor profiles (idempotent)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users (keycloak_id, username, email, display_name, password, enabled, role)
VALUES ('kc_vendor1_sub', 'vendor1', 'vendor1@example.com', 'Vendor One',
        crypt('Vendor123!', gen_salt('bf')), TRUE, 'ROLE_VENDOR'),
       ('kc_vendor2_sub', 'vendor2', 'vendor2@example.com', 'Vendor Two',
        crypt('Vendor123!', gen_salt('bf')), TRUE, 'ROLE_VENDOR')
ON CONFLICT (keycloak_id) DO NOTHING;

UPDATE users
SET role = 'ROLE_VENDOR',
    enabled = TRUE,
    password = crypt('Vendor123!', gen_salt('bf'))
WHERE username IN ('vendor1', 'vendor2');

INSERT INTO vendors (user_id, status, shop_name, legal_name, vat_number, iban, created_at, updated_at)
SELECT u.id, 'APPROVED', 'Vendor One Shop', 'Vendor One LLC', 'VEND001', 'TR000000000000000000000001', NOW(), NOW()
FROM users u
WHERE u.username = 'vendor1'
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO vendors (user_id, status, shop_name, legal_name, vat_number, iban, created_at, updated_at)
SELECT u.id, 'APPROVED', 'Vendor Two Atelier', 'Vendor Two SAS', 'VEND002', 'TR000000000000000000000002', NOW(), NOW()
FROM users u
WHERE u.username = 'vendor2'
ON CONFLICT (user_id) DO NOTHING;
