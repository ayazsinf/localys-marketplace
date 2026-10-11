-- Speeds up "listings near me" searches, which filter on a latitude/longitude box first.
CREATE INDEX IF NOT EXISTS idx_products_lat_lng ON products (latitude, longitude)
    WHERE latitude IS NOT NULL AND longitude IS NOT NULL;
