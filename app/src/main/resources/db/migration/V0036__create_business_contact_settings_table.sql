-- Dispute/refund/support routing (Settings -> Contact). One row per
-- business; generalEmail is the only required field - the frontend defaults
-- it to the signed-up account email until the business overrides it.
CREATE TABLE business_contact_settings (
    id               UUID PRIMARY KEY,
    status           VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       UUID,
    deleted_at       TIMESTAMPTZ,
    business_id      UUID NOT NULL REFERENCES business (id),
    -- Comma-separated email lists, not a native Postgres array - keeps the
    -- JPA mapping a plain String (no array-type converter dependency),
    -- consistent with this codebase's other free-form text columns (e.g.
    -- VirtualAccount.meta). Split/joined in BusinessContactSettings.
    dispute_emails   TEXT NOT NULL DEFAULT '',
    refund_emails    TEXT NOT NULL DEFAULT '',
    support_email    VARCHAR(128),
    general_email    VARCHAR(128) NOT NULL
);

CREATE UNIQUE INDEX idx_business_contact_settings_business_id ON business_contact_settings (business_id);
