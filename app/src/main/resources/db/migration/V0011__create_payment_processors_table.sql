-- Onboarded processor integrations (FR-6). Activation restricted to the
-- Supply Admin role (processors:configure). Whether new transactions may
-- route through it is governed by status (ACTIVE/INACTIVE).
CREATE TABLE payment_processors (
    id          UUID PRIMARY KEY,
    status      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    deleted_at  TIMESTAMPTZ,
    name        VARCHAR(64) NOT NULL
);
