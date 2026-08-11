CREATE TABLE business (
    id           UUID PRIMARY KEY,
    status       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   UUID,
    deleted_at   TIMESTAMPTZ,
    name         VARCHAR(128) NOT NULL,
    cac_number   VARCHAR(32),
    address_id   UUID,
    owner_id     UUID NOT NULL REFERENCES users (id)
);

CREATE INDEX idx_business_owner_id ON business (owner_id);
