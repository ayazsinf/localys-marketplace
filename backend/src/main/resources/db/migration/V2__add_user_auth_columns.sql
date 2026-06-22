-- Add auth columns for users and seed roles/passwords
CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password VARCHAR(200) NOT NULL DEFAULT crypt('ChangeMe123!', gen_salt('bf')),
    ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS role VARCHAR(50) NOT NULL DEFAULT 'ROLE_USER';

-- Ensure existing demo users get roles and passwords
UPDATE users
SET role = 'ROLE_ADMIN',
    password = crypt('Admin123!', gen_salt('bf'))
WHERE username = 'admin';

UPDATE users
SET role = 'ROLE_VENDOR',
    password = crypt('Vendor123!', gen_salt('bf'))
WHERE username IN ('vendor1', 'vendor2');
