-- Categories table
CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    icon_url TEXT,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed basic categories
INSERT INTO categories (name, description, display_order) VALUES 
('Appetizers', 'Small dishes to start your meal', 1),
('Mains', 'Hearty and delicious main courses', 2),
('Desserts', 'Sweet treats to finish your meal', 3),
('Drinks', 'Refreshing beverages', 4);

-- Add category_id to menu_items
-- For existing items, we assign them to 'Mains' (id 2)
ALTER TABLE menu_items
ADD COLUMN category_id BIGINT REFERENCES categories(id);

UPDATE menu_items SET category_id = (SELECT id FROM categories WHERE name = 'Mains');

ALTER TABLE menu_items ALTER COLUMN category_id SET NOT NULL;

-- Full-text search index
CREATE INDEX menu_items_search_idx ON menu_items
USING gin(to_tsvector('english', name || ' ' || description));
