-- One allowed IP/CIDR entry for an API key (doc 3 §2.3(b)/§2.5, FR-9). An
-- empty whitelist for a key means unrestricted (ADR-8, doc 2 §7).
CREATE TABLE api_key_ip_whitelist (
    id          UUID PRIMARY KEY,
    status      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    deleted_at  TIMESTAMPTZ,
    api_key_id  UUID NOT NULL REFERENCES api_keys (id),
    cidr        VARCHAR(64) NOT NULL
);

CREATE UNIQUE INDEX idx_api_key_ip_whitelist_key_cidr ON api_key_ip_whitelist (api_key_id, cidr);
