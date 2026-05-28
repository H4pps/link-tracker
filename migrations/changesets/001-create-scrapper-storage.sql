CREATE TABLE chats (
    id BIGSERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_chats_chat_id UNIQUE (chat_id)
);

CREATE TABLE links (
    id BIGSERIAL PRIMARY KEY,
    url TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_checked_at TIMESTAMPTZ,
    last_seen_external_updated_at TIMESTAMPTZ,
    CONSTRAINT uq_links_url UNIQUE (url)
);

CREATE TABLE subscriptions (
    id BIGSERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL,
    link_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_subscriptions_chat_id FOREIGN KEY (chat_id) REFERENCES chats (id) ON DELETE CASCADE,
    CONSTRAINT fk_subscriptions_link_id FOREIGN KEY (link_id) REFERENCES links (id) ON DELETE CASCADE,
    CONSTRAINT uq_subscriptions_chat_link UNIQUE (chat_id, link_id)
);

CREATE INDEX idx_subscriptions_link_id ON subscriptions (link_id);

CREATE TABLE tags (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tags_name UNIQUE (name)
);

CREATE TABLE subscription_tags (
    subscription_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    CONSTRAINT pk_subscription_tags PRIMARY KEY (subscription_id, tag_id),
    CONSTRAINT fk_subscription_tags_subscription_id
        FOREIGN KEY (subscription_id) REFERENCES subscriptions (id) ON DELETE CASCADE,
    CONSTRAINT fk_subscription_tags_tag_id FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE RESTRICT
);

CREATE INDEX idx_subscription_tags_tag_id ON subscription_tags (tag_id);

CREATE TABLE subscription_filters (
    id BIGSERIAL PRIMARY KEY,
    subscription_id BIGINT NOT NULL,
    value TEXT NOT NULL,
    CONSTRAINT fk_subscription_filters_subscription_id
        FOREIGN KEY (subscription_id) REFERENCES subscriptions (id) ON DELETE CASCADE,
    CONSTRAINT uq_subscription_filters_subscription_id_value UNIQUE (subscription_id, value)
);

CREATE TABLE link_update_checkpoints (
    link_id BIGINT PRIMARY KEY,
    last_seen_external_updated_at TIMESTAMPTZ NOT NULL,
    checked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_link_update_checkpoints_link_id FOREIGN KEY (link_id) REFERENCES links (id) ON DELETE CASCADE
);

CREATE INDEX idx_link_update_checkpoints_checked_at ON link_update_checkpoints (checked_at);
