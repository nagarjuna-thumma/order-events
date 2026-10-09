CREATE TABLE orders (
    id            UUID PRIMARY KEY,
    customer_id   VARCHAR(64)    NOT NULL,
    sku           VARCHAR(64)    NOT NULL,
    quantity      INT            NOT NULL CHECK (quantity > 0),
    total_amount  NUMERIC(12, 2) NOT NULL,
    status        VARCHAR(32)    NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL
);

CREATE TABLE outbox_events (
    id            UUID PRIMARY KEY,
    topic         VARCHAR(255) NOT NULL,
    message_key   VARCHAR(255) NOT NULL,
    payload       TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    published_at  TIMESTAMPTZ
);

-- The relay only ever looks for unpublished rows, so keep that lookup cheap.
CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
