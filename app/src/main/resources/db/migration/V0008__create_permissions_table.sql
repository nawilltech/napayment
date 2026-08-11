CREATE TABLE permissions (
    id          UUID PRIMARY KEY,
    status      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    deleted_at  TIMESTAMPTZ,
    name        VARCHAR(64) NOT NULL,
    resource    VARCHAR(32) NOT NULL,
    action      VARCHAR(32) NOT NULL
);

CREATE UNIQUE INDEX idx_permissions_name ON permissions (name);
