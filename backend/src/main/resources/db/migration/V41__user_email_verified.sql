-- Whether Keycloak confirmed the user's current email address; refreshed at login.
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;
