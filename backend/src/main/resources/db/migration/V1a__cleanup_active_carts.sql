-- Ensure a single ACTIVE cart per user before unique index creation
WITH ranked AS (
    SELECT id,
           user_id,
           ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at DESC, id DESC) AS rn
    FROM carts
    WHERE status = 'ACTIVE'
)
UPDATE carts c
SET status = 'ABANDONED'
FROM ranked r
WHERE c.id = r.id
  AND r.rn > 1;
