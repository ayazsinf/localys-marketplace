-- Shipping: each seller ships their own items of an order.
ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS tracking_number VARCHAR(100),
    ADD COLUMN IF NOT EXISTS shipped_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMPTZ;

-- Conversations are about a listing (NULL for legacy user-to-user conversations).
ALTER TABLE conversations
    ADD COLUMN IF NOT EXISTS product_id BIGINT REFERENCES products(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_conversations_product
    ON conversations(product_id);

-- Unread message counters.
CREATE INDEX IF NOT EXISTS idx_messages_unread
    ON messages(conversation_id)
    WHERE read_at IS NULL;

-- Public listing search.
CREATE INDEX IF NOT EXISTS idx_products_public
    ON products(moderation_status, active, expires_at);

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS notifications_type_check;

ALTER TABLE notifications
    ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('PRODUCT_CREATED', 'PRODUCT_PENDING_REVIEW', 'PRODUCT_APPROVED', 'PRODUCT_REJECTED',
                    'FAVORITE_ADDED', 'ORDER_PAID', 'ORDER_SOLD', 'ORDER_SHIPPED', 'ORDER_DELIVERED'));
