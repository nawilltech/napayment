-- The account a user/business receives collections into, auto-provisioned
-- at signup (FR-1).
CREATE TABLE virtual_accounts (
    id              UUID PRIMARY KEY,
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    deleted_at      TIMESTAMPTZ,
    account_number  VARCHAR(20) NOT NULL,
    user_id         UUID REFERENCES users (id),
    business_id     UUID REFERENCES business (id),
    currency        VARCHAR(3) NOT NULL DEFAULT 'NGN',
    balance         NUMERIC(19, 0) NOT NULL DEFAULT 0,
    meta            TEXT,
    CONSTRAINT chk_virtual_accounts_owner CHECK (
        (user_id IS NOT NULL AND business_id IS NULL) OR
        (user_id IS NULL AND business_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX idx_virtual_accounts_account_number ON virtual_accounts (account_number);
CREATE INDEX idx_virtual_accounts_user_id ON virtual_accounts (user_id);
CREATE INDEX idx_virtual_accounts_business_id ON virtual_accounts (business_id);
