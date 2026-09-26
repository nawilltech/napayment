-- PCI DSS 8.3.7: a new password must not match any of the account's last 4
-- passwords. Hashes only, same BCrypt encoder as users.password_hash -
-- plaintext is never stored here or anywhere else.
CREATE TABLE password_history (
    id             UUID PRIMARY KEY,
    status         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     UUID,
    deleted_at     TIMESTAMPTZ,
    user_id        UUID NOT NULL REFERENCES users (id),
    password_hash  VARCHAR(255) NOT NULL
);

CREATE INDEX idx_password_history_user_id_created_at ON password_history (user_id, created_at DESC);
