CREATE TABLE market_discussion_post (
    id UUID PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL CHECK (symbol ~ '^[A-Z0-9]{2,20}$'),
    user_id UUID NOT NULL REFERENCES app_user(id),
    content VARCHAR(1000) NOT NULL CHECK (char_length(btrim(content)) BETWEEN 1 AND 1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_market_discussion_post_symbol_created
    ON market_discussion_post (symbol, created_at DESC, id DESC);

COMMENT ON TABLE market_discussion_post IS 'Per-coin public discussion posts; user posts are deleted on account deletion';
COMMENT ON COLUMN market_discussion_post.user_id IS 'Author account ID; never exposed in public responses';
