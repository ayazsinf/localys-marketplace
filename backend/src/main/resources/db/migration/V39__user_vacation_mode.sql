-- Sellers can go away for a while: their listings are hidden and cannot be bought meanwhile.
ALTER TABLE users ADD COLUMN IF NOT EXISTS vacation_mode BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS vacation_message VARCHAR(300);
