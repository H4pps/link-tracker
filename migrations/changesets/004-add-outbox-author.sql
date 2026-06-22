ALTER TABLE link_update_outbox
    ADD COLUMN payload_author TEXT NOT NULL DEFAULT '';
