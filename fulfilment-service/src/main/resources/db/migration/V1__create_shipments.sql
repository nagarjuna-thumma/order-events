CREATE TABLE shipments (
    id          UUID PRIMARY KEY,
    order_id    UUID         NOT NULL UNIQUE,
    sku         VARCHAR(64)  NOT NULL,
    quantity    INT          NOT NULL,
    status      VARCHAR(32)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE processed_events (
    event_id      UUID PRIMARY KEY,
    processed_at  TIMESTAMPTZ NOT NULL
);
