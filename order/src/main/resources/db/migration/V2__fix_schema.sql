-- Drop existing payment_methods as it's fundamentally different
DROP TABLE payment_methods;

CREATE TABLE payment_methods (
    id BIGSERIAL PRIMARY KEY,
    buyer_id VARCHAR(255) NOT NULL,
    card_type VARCHAR(20) NOT NULL,
    card_holder_name VARCHAR(255) NOT NULL,
    card_number_masked VARCHAR(4) NOT NULL,
    expiration_month INT NOT NULL CHECK (expiration_month BETWEEN 1 AND 12),
    expiration_year INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT payment_methods_unique UNIQUE (buyer_id, card_type, card_number_masked)
);

ALTER TABLE orders ADD COLUMN request_id VARCHAR(255) UNIQUE;
-- Populate request_id for existing orders to allow NOT NULL constraint
UPDATE orders SET request_id = 'migrated-' || id WHERE request_id IS NULL;
ALTER TABLE orders ALTER COLUMN request_id SET NOT NULL;

ALTER TABLE orders ADD COLUMN payment_method_id BIGINT REFERENCES payment_methods(id);
