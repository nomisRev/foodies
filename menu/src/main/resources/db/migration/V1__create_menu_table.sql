CREATE TABLE menu_items (
    id          BIGSERIAL PRIMARY KEY,
    name        TEXT                     NOT NULL,
    description TEXT                     NOT NULL,
    image_url   TEXT                     NOT NULL,
    price       NUMERIC(10, 2)           NOT NULL CHECK (price > 0),
    created_at  TIMESTAMPTZ              NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ              NOT NULL DEFAULT now()
);

CREATE INDEX menu_items_name_lower_idx ON menu_items (lower(name));