-- NFR-7: rotating, server-revocable refresh tokens. Postgres is the source
-- of truth (not Redis) - see docs discussion: durability across a
-- Redis restart/eviction matters for a 30-day session token in a way it
-- doesn't for a 15-minute lockout counter, and a compromised-account
-- investigation needs a permanent record of every token issued/revoked for
-- a user, not a 5-minute tombstone. token_hash is a SHA-256 hex digest - the
-- raw token is only ever returned to the client once, never stored.
CREATE TABLE refresh_tokens (
    id                     UUID PRIMARY KEY,
    status                 VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by             UUID,
    deleted_at             TIMESTAMPTZ,
    user_id                UUID NOT NULL REFERENCES users (id),
    token_hash             VARCHAR(64) NOT NULL,
    expires_at             TIMESTAMPTZ NOT NULL,
    revoked_at             TIMESTAMPTZ,
    replaced_by_token_id   UUID REFERENCES refresh_tokens (id)
);

CREATE UNIQUE INDEX idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

-- NFR-7 / PCI DSS 10.2.1, 10.3.1, 10.5.5: append-only security/activity
-- audit trail for auth and onboarding events. No UPDATE/DELETE path exists
-- anywhere in application code (SecurityAuditLogRepository only ever
-- saves/reads) - the practical, code-level version of "immutable once
-- written" a single Postgres instance can offer without WORM storage or a
-- separate log-shipping pipeline (documented as future hardening, not
-- pretended to be done here).
CREATE TABLE security_audit_log (
    id           UUID PRIMARY KEY,
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    event_type   VARCHAR(48) NOT NULL,
    outcome      VARCHAR(16) NOT NULL,
    user_id      UUID,
    business_id  UUID,
    email        VARCHAR(128),
    ip_address   VARCHAR(64),
    user_agent   VARCHAR(256),
    detail       VARCHAR(512)
);

CREATE INDEX idx_security_audit_log_user_id ON security_audit_log (user_id);
CREATE INDEX idx_security_audit_log_business_id ON security_audit_log (business_id);
CREATE INDEX idx_security_audit_log_event_type ON security_audit_log (event_type);
CREATE INDEX idx_security_audit_log_occurred_at ON security_audit_log (occurred_at);
