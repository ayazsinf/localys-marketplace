-- Why a message may be a scam (paying outside the platform, external link, contact details).
ALTER TABLE messages ADD COLUMN IF NOT EXISTS safety_warning VARCHAR(30);
