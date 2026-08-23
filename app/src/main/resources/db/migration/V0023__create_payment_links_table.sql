-- A shareable link a payer uses to pay a business (FR-14, doc 4 §C.9).
-- short_code is the short URL itself. amount NULL means the payer enters
-- their own amount at pay time.
CREATE TABLE payment_links (
    id                  UUID PRIMARY KEY,
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID,
    deleted_at          TIMESTAMPTZ,
    business_id         UUID NOT NULL,
    virtual_account_id  UUID NOT NULL REFERENCES virtual_accounts (id),
    short_code          VARCHAR(16) NOT NULL,
    amount              NUMERIC(19, 0),
    currency            VARCHAR(3) NOT NULL,
    link_type           VARCHAR(16) NOT NULL,
    expires_at          TIMESTAMPTZ,
    single_use          BOOLEAN NOT NULL,
    link_status         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
);

CREATE UNIQUE INDEX idx_payment_links_short_code ON payment_links (short_code);
CREATE INDEX idx_payment_links_business_id ON payment_links (business_id);
