ALTER TABLE link_update_outbox
    ADD COLUMN message_id UUID NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX idx_link_update_outbox_message_id ON link_update_outbox (message_id);
