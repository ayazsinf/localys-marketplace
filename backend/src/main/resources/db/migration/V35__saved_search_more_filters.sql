-- Saved searches remember every home-page filter, not just text, category and price.
ALTER TABLE saved_searches ADD COLUMN IF NOT EXISTS conditions VARCHAR(100);
ALTER TABLE saved_searches ADD COLUMN IF NOT EXISTS delivery VARCHAR(10);
ALTER TABLE saved_searches ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION;
ALTER TABLE saved_searches ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;
ALTER TABLE saved_searches ADD COLUMN IF NOT EXISTS radius_km DOUBLE PRECISION;
ALTER TABLE saved_searches ADD COLUMN IF NOT EXISTS location_label VARCHAR(200);
