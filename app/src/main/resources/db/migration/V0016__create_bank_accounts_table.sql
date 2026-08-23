-- A business's registered real bank accounts, later attached to a
-- settlement config with a split percentage (FR-2, doc 2 §4.2).
CREATE TABLE bank_accounts (
    id              UUID PRIMARY KEY,
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    deleted_at      TIMESTAMPTZ,
    bank_id         UUID NOT NULL REFERENCES banks (id),
    account_number  VARCHAR(20) NOT NULL,
    account_name    VARCHAR(128) NOT NULL,
    business_id     UUID NOT NULL REFERENCES business (id)
);

CREATE INDEX idx_bank_accounts_business_id ON bank_accounts (business_id);
