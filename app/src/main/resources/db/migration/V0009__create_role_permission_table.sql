CREATE TABLE role_permission (
    id             UUID PRIMARY KEY,
    status         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     UUID,
    deleted_at     TIMESTAMPTZ,
    role_id        UUID NOT NULL REFERENCES roles (id),
    permission_id  UUID NOT NULL REFERENCES permissions (id)
);

CREATE UNIQUE INDEX idx_role_permission_role_id_permission_id ON role_permission (role_id, permission_id);
CREATE INDEX idx_role_permission_permission_id ON role_permission (permission_id);
