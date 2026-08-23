-- Nawill Pay's single pooled bank account (doc 1 §1.3 glossary, doc 3 §3's
-- reconciliation invariant) - where real inbound transfers land before
-- internal ledger allocation. Admin-only to create (collection-account:manage
-- is unassigned to any seeded role - SUPERADMIN bypass only). The partial
-- unique index enforces the singleton at the DB level: at most one row can
-- ever have status = 'ACTIVE', regardless of application-level races.
CREATE TABLE collection_accounts (
    id              UUID PRIMARY KEY,
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    deleted_at      TIMESTAMPTZ,
    bank_id         UUID NOT NULL REFERENCES banks (id),
    account_number  VARCHAR(20) NOT NULL,
    account_name    VARCHAR(128) NOT NULL,
    balance         NUMERIC(19, 0) NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_collection_accounts_single_active ON collection_accounts ((true)) WHERE status = 'ACTIVE';
