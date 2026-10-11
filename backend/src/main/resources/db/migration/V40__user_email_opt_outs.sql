-- Email categories a user switched off (comma-separated); null means all emails are wanted.
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_opt_outs VARCHAR(200);
