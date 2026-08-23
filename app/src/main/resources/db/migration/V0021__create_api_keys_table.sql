-- A business's public/secret key pair (FR-9), used to HMAC-sign
-- third-party requests to /api/v1/collect and /api/v1/withdraw (doc 3
-- §2.5). secret_key_encrypted is reversible (AES-GCM), never a one-way
-- hash - verifying a signature requires recovering the plaintext secret.
-- The partial unique index enforces one active pair per business;
-- regenerating revokes the old row (status INACTIVE) rather than deleting
-- it, so it stays around for audit and the index doesn't block re-issuance.
CREATE TABLE api_keys (
    id                     UUID PRIMARY KEY,
    status                 VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by             UUID,
    deleted_at             TIMESTAMPTZ,
    business_id            UUID NOT NULL,
    public_key             VARCHAR(64) NOT NULL,
    secret_key_encrypted   TEXT NOT NULL
);

CREATE UNIQUE INDEX idx_api_keys_public_key ON api_keys (public_key);
CREATE UNIQUE INDEX idx_api_keys_business_id_active ON api_keys (business_id) WHERE status = 'ACTIVE';
