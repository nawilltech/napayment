-- The bank account a virtual account's funds settle to, with a split
-- percentage (FR-2, doc 2 §4.2). SettlementAccountService rejects a
-- create that would push the sum of active percentages for one virtual
-- account above 100 (application-level guard; the row lock on
-- virtual_accounts during that check is what makes it race-safe).
CREATE TABLE settlement_accounts (
    id                  UUID PRIMARY KEY,
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID,
    deleted_at          TIMESTAMPTZ,
    virtual_account_id  UUID NOT NULL REFERENCES virtual_accounts (id),
    bank_account_id     UUID NOT NULL REFERENCES bank_accounts (id),
    split_percentage    NUMERIC(5, 2) NOT NULL
);

CREATE INDEX idx_settlement_accounts_virtual_account_id ON settlement_accounts (virtual_account_id);
CREATE UNIQUE INDEX idx_settlement_accounts_va_bank_account ON settlement_accounts (virtual_account_id, bank_account_id);
