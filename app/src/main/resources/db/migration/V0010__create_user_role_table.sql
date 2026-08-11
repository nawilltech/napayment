CREATE TABLE user_role (
    id          UUID PRIMARY KEY,
    status      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    deleted_at  TIMESTAMPTZ,
    user_id     UUID NOT NULL REFERENCES users (id),
    role_id     UUID NOT NULL REFERENCES roles (id)
);

CREATE UNIQUE INDEX idx_user_role_user_id_role_id ON user_role (user_id, role_id);
CREATE INDEX idx_user_role_role_id ON user_role (role_id);
