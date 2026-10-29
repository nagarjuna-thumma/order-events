CREATE TABLE webhook_subscriptions (
    id          UUID PRIMARY KEY,
    url         VARCHAR(2000) NOT NULL,
    secret      VARCHAR(100)  NOT NULL,
    active      BOOLEAN       NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL
);

CREATE TABLE webhook_deliveries (
    id                UUID PRIMARY KEY,
    subscription_id   UUID          NOT NULL REFERENCES webhook_subscriptions (id),
    event_type        VARCHAR(64)   NOT NULL,
    payload           TEXT          NOT NULL,
    status            VARCHAR(16)   NOT NULL,
    attempts          INT           NOT NULL,
    next_attempt_at   TIMESTAMPTZ   NOT NULL,
    last_status_code  INT,
    last_error        VARCHAR(500),
    created_at        TIMESTAMPTZ   NOT NULL,
    delivered_at      TIMESTAMPTZ
);

-- The dispatcher's poll: due pending rows, oldest first.
CREATE INDEX idx_webhook_deliveries_due ON webhook_deliveries (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX idx_webhook_deliveries_subscription ON webhook_deliveries (subscription_id, created_at);
