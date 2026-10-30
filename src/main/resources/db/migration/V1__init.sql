CREATE TABLE charges (
    id              UUID PRIMARY KEY,
    txid            VARCHAR(35)    NOT NULL UNIQUE,
    amount          NUMERIC(15, 2) NOT NULL,
    description     VARCHAR(140),
    payer_name      VARCHAR(100),
    payer_document  VARCHAR(14),
    status          VARCHAR(16)    NOT NULL,
    br_code         VARCHAR(512)   NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    paid_at         TIMESTAMP WITH TIME ZONE,
    end_to_end_id   VARCHAR(32) UNIQUE,
    version         BIGINT         NOT NULL
);

CREATE INDEX idx_charges_status_expires ON charges (status, expires_at);
CREATE INDEX idx_charges_paid_at ON charges (paid_at);

CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(64) PRIMARY KEY,
    request_hash    VARCHAR(64) NOT NULL,
    response_status INT         NOT NULL,
    response_body   TEXT        NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(40)  NOT NULL,
    aggregate_id    VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(80)  NOT NULL,
    payload         TEXT         NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    attempts        INT          NOT NULL,
    last_error      VARCHAR(500),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at    TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_pending ON outbox_events (status, next_attempt_at, created_at);
