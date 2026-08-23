-- One disbursement out of a virtual account to one of its settlement
-- accounts (doc 3 §1.1: "settle, disburse" is idempotency-protected, same
-- as a transaction). version backs optimistic locking on the
-- PENDING -> PROCESSING -> COMPLETED/FAILED state machine, mirroring
-- transactions.
CREATE TABLE settlements (
    id                     UUID PRIMARY KEY,
    status                 VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by             UUID,
    deleted_at             TIMESTAMPTZ,
    virtual_account_id     UUID NOT NULL REFERENCES virtual_accounts (id),
    settlement_account_id  UUID NOT NULL REFERENCES settlement_accounts (id),
    amount                 NUMERIC(19, 0) NOT NULL,
    settlement_status      VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reference              VARCHAR(128),
    idempotency_key        VARCHAR(128) NOT NULL,
    version                BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_settlements_idempotency_key ON settlements (idempotency_key);
CREATE INDEX idx_settlements_virtual_account_id ON settlements (virtual_account_id);
