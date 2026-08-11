-- Generic reusable idempotency cache backing the @Idempotent interceptor
-- (common-core). Distinct from transactions.idempotency_key, which is the
-- transaction-domain-specific unique constraint (doc 3 §1.2); this table is
-- the request/response replay cache for any @Idempotent endpoint.
CREATE TABLE idempotency_records (
    id               UUID PRIMARY KEY,
    status           VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       UUID,
    deleted_at       TIMESTAMPTZ,
    idempotency_key  VARCHAR(128) NOT NULL,
    http_method      VARCHAR(8) NOT NULL,
    request_path     VARCHAR(256) NOT NULL,
    response_status  INTEGER,
    response_body    TEXT,
    completed_at     TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_idempotency_records_idempotency_key ON idempotency_records (idempotency_key);
