-- Ratings belong to users, not shops: after a sale the buyer rates the seller and the
-- seller rates the buyer (one review per order item and reviewer).
ALTER TABLE reviews
    ADD COLUMN IF NOT EXISTS reviewee_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS reviewee_role VARCHAR(10) NOT NULL DEFAULT 'SELLER';

-- vendor_id still exists here (created in V26, dropped below).
UPDATE reviews r
SET reviewee_id = v.user_id
FROM vendors v
WHERE v.id = r.vendor_id
  AND r.reviewee_id IS NULL;

ALTER TABLE reviews ALTER COLUMN reviewee_id SET NOT NULL;

ALTER TABLE reviews DROP CONSTRAINT IF EXISTS reviews_order_item_id_key;
CREATE UNIQUE INDEX IF NOT EXISTS ux_reviews_item_reviewer
    ON reviews(order_item_id, reviewer_id);

ALTER TABLE reviews DROP COLUMN IF EXISTS vendor_id;

CREATE INDEX IF NOT EXISTS idx_reviews_reviewee_created
    ON reviews(reviewee_id, created_at DESC);
