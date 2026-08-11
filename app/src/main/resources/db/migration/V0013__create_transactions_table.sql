-- The core transaction ledger (FR-Txn-1/2). idempotency_key's unique
-- constraint is the source of truth for idempotency (doc 3 §1.2); version
-- backs optimistic locking on the PENDING -> PROCESSING -> PAID/FAILED state
-- machine. TODO(FR-2): recipient_account_id (external settlement via
-- SettlementAccount/BankAccount) is v0.2 - MVP credits/debits
-- virtual_account_id directly.
CREATE TABLE transactions (
    id                    UUID PRIMARY KEY,
    status                VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by            UUID,
    deleted_at            TIMESTAMPTZ,
    amount                NUMERIC(19, 0) NOT NULL,
    charge                NUMERIC(19, 0) NOT NULL DEFAULT 0,
    idempotency_key       VARCHAR(128) NOT NULL,
    payment_processor_id  UUID NOT NULL REFERENCES payment_processors (id),
    transaction_status    VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    transaction_type      VARCHAR(8) NOT NULL,
    session_id            VARCHAR(64) NOT NULL,
    virtual_account_id    UUID NOT NULL REFERENCES virtual_accounts (id),
    version               BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_transactions_idempotency_key ON transactions (idempotency_key);
CREATE INDEX idx_transactions_virtual_account_id ON transactions (virtual_account_id);
CREATE INDEX idx_transactions_payment_processor_id ON transactions (payment_processor_id);
CREATE INDEX idx_transactions_created_at ON transactions (created_at);
