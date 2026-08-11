-- business_id has no FK yet - business (V0005) doesn't exist until after this
-- migration; V0006 backfills the FK once both tables exist (circular
-- reference: users.business_id <-> business.owner_id).
CREATE TABLE users (
    id             UUID PRIMARY KEY,
    status         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     UUID,
    deleted_at     TIMESTAMPTZ,
    first_name     VARCHAR(64) NOT NULL,
    middle_name    VARCHAR(64),
    last_name      VARCHAR(64) NOT NULL,
    email          VARCHAR(128) NOT NULL,
    phone_no       VARCHAR(20) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    is_verified    BOOLEAN NOT NULL DEFAULT FALSE,
    dob            DATE,
    user_type      VARCHAR(16) NOT NULL,
    address_id     UUID,
    business_id    UUID
);

CREATE UNIQUE INDEX idx_users_email ON users (email);
CREATE UNIQUE INDEX idx_users_phone_no ON users (phone_no);
CREATE INDEX idx_users_business_id ON users (business_id);
