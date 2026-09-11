-- FR-8 BVN/NIN verification for the business owner. One row per business -
-- bvn_encrypted/nin_encrypted are AES-256-GCM ciphertext (EncryptionService),
-- never plaintext, same posture as api_keys.secret_key_encrypted.
CREATE TABLE owner_identities (
    id                   UUID PRIMARY KEY,
    status               VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by           UUID,
    deleted_at           TIMESTAMPTZ,
    business_id          UUID NOT NULL REFERENCES business (id),
    bvn_encrypted         TEXT,
    nin_encrypted         TEXT,
    verified             BOOLEAN NOT NULL DEFAULT FALSE,
    provider_reference   VARCHAR(64),
    verified_at          TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_owner_identities_business_id ON owner_identities (business_id);
