CREATE TABLE link_update_outbox (
    id BIGSERIAL PRIMARY KEY,
    payload_id BIGINT NOT NULL,
    payload_url TEXT NOT NULL,
    payload_description TEXT NOT NULL,
    payload_tg_chat_ids TEXT NOT NULL,
    status TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at TIMESTAMPTZ,
    CONSTRAINT chk_link_update_outbox_status CHECK (status IN ('PENDING', 'SENT'))
);

CREATE INDEX idx_link_update_outbox_polling
    ON link_update_outbox (status, next_attempt_at, id)
    WHERE status = 'PENDING';

CREATE INDEX idx_link_update_outbox_sent_at ON link_update_outbox (sent_at);
