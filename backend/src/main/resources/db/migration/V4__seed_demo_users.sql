-- Seed demo users and set credentials/roles
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users (keycloak_id, username, email, display_name, password, enabled, role)
VALUES ('kc_testuser_sub', 'testuser', 'testuser@example.com', 'Test User',
        crypt('User123!', gen_salt('bf')), TRUE, 'ROLE_USER'),
       ('kc_admin_sub', 'admin', 'admin@example.com', 'Admin User',
        crypt('Admin123!', gen_salt('bf')), TRUE, 'ROLE_ADMIN')
ON CONFLICT (keycloak_id) DO NOTHING;

UPDATE users
SET role = 'ROLE_ADMIN',
    enabled = TRUE,
    password = crypt('Admin123!', gen_salt('bf'))
WHERE username = 'admin';

UPDATE users
SET role = 'ROLE_USER',
    enabled = TRUE,
    password = crypt('User123!', gen_salt('bf'))
WHERE username = 'testuser';

UPDATE users
SET role = 'ROLE_VENDOR',
    enabled = TRUE,
    password = crypt('Vendor123!', gen_salt('bf'))
WHERE username IN ('vendor1', 'vendor2');

-- Demo vendor (owner user)
INSERT INTO vendors (user_id, status, shop_name, legal_name, vat_number, created_at, updated_at)
SELECT u.id, 'APPROVED', 'Localys Shop', 'Localys SARL', 'FRXX999999', NOW(), NOW()
FROM users u
WHERE u.username = 'admin'
ON CONFLICT (user_id) DO NOTHING;
