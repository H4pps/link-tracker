CREATE TABLE IF NOT EXISTS processed_link_updates (
    message_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
