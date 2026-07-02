ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS notifications_type_check;

ALTER TABLE notifications
    ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('PRODUCT_CREATED', 'PRODUCT_PENDING_REVIEW', 'PRODUCT_APPROVED', 'PRODUCT_REJECTED', 'FAVORITE_ADDED'));
