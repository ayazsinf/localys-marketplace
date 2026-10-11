-- A photo sent in a conversation; the text may then be empty.
ALTER TABLE messages ADD COLUMN IF NOT EXISTS image_url VARCHAR(500);
