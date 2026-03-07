CREATE TABLE IF NOT EXISTS payment_methods (
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

CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(255) UNIQUE NOT NULL,
    buyer_id VARCHAR(255) NOT NULL,
    buyer_email VARCHAR(255) NOT NULL,
    buyer_name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    total_price DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    description TEXT,
    street VARCHAR(255) NOT NULL,
    city VARCHAR(255) NOT NULL,
    state VARCHAR(255) NOT NULL,
    country VARCHAR(255) NOT NULL,
    zip_code VARCHAR(50) NOT NULL,
    payment_method_id BIGINT REFERENCES payment_methods(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    menu_item_id BIGINT NOT NULL,
    menu_item_name VARCHAR(255) NOT NULL,
    picture_url TEXT NOT NULL,
    unit_price DECIMAL(19, 4) NOT NULL,
    quantity INT NOT NULL,
    discount DECIMAL(19, 4) NOT NULL
);

CREATE TABLE IF NOT EXISTS order_history (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS order_history_order_id_idx ON order_history(order_id);
