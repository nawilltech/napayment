CREATE TABLE banks (
    id          UUID PRIMARY KEY,
    status      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    deleted_at  TIMESTAMPTZ,
    name        VARCHAR(128) NOT NULL,
    code        VARCHAR(16) NOT NULL
);

CREATE UNIQUE INDEX idx_banks_code ON banks (code);
