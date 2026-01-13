CREATE TABLE processed_requests (
    request_id UUID PRIMARY KEY,
    command_type VARCHAR(100) NOT NULL,
    result JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX processed_requests_created_at_idx ON processed_requests(created_at);
