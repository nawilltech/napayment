-- A per-transaction, expiring bank-style account number (FR-DynAcct-1,
-- doc 4 §C.10) - the transfer-based sibling of a payment link, solving
-- bank-transfer reconciliation. Deposits land on the parent
-- virtual_account_id (the business's real, permanent account); this table
-- is a routing/reconciliation shell around it, not a second ledger.
CREATE TABLE dynamic_virtual_accounts (
    id                      UUID PRIMARY KEY,
    status                  VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by              UUID,
    deleted_at              TIMESTAMPTZ,
    business_id             UUID NOT NULL,
    virtual_account_id      UUID NOT NULL REFERENCES virtual_accounts (id),
    account_number          VARCHAR(20) NOT NULL,
    expected_amount         NUMERIC(19, 0),
    expires_at              TIMESTAMPTZ NOT NULL,
    dynamic_account_status  VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    reference               VARCHAR(128)
);

CREATE UNIQUE INDEX idx_dynamic_virtual_accounts_account_number ON dynamic_virtual_accounts (account_number);
CREATE INDEX idx_dynamic_virtual_accounts_business_id ON dynamic_virtual_accounts (business_id);
