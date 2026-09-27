-- FR-Proc-1..4, FR-Admin-5..7: platform processor catalogue with payment
-- methods, per-business processor settings, and business suspension.
-- Behaviour-neutral on deploy: every existing processor stays available to
-- every business (default_enabled) and offers TRANSFER, which is what all
-- collections implicitly were until now.

-- Processor catalogue: a stable code (which integration/config it maps to),
-- a routing priority (lower first), and the "for all businesses" default.
ALTER TABLE payment_processors
    ADD COLUMN code            VARCHAR(32),
    ADD COLUMN priority        INTEGER NOT NULL DEFAULT 100,
    ADD COLUMN default_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- Backfill codes from names; duplicate names (possible before names were
-- validated as unique) get a short id suffix so the unique index holds.
WITH slugged AS (
    SELECT id,
           UPPER(TRIM(BOTH '_' FROM REGEXP_REPLACE(name, '[^A-Za-z0-9]+', '_', 'g'))) AS slug,
           ROW_NUMBER() OVER (
               PARTITION BY UPPER(TRIM(BOTH '_' FROM REGEXP_REPLACE(name, '[^A-Za-z0-9]+', '_', 'g')))
               ORDER BY created_at, id) AS n
    FROM payment_processors
)
UPDATE payment_processors p
SET code = LEFT(CASE WHEN s.n = 1 THEN s.slug ELSE s.slug || '_' || LEFT(REPLACE(p.id::text, '-', ''), 6) END, 32)
FROM slugged s
WHERE p.id = s.id;

ALTER TABLE payment_processors ALTER COLUMN code SET NOT NULL;
CREATE UNIQUE INDEX idx_payment_processors_code ON payment_processors (code);

-- Payment methods a processor offers (children of the processor). method is
-- one of the fixed PaymentMethod values; status ACTIVE/INACTIVE enables or
-- retires it without losing history.
CREATE TABLE payment_processor_methods (
    id            UUID PRIMARY KEY,
    status        VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    deleted_at    TIMESTAMPTZ,
    processor_id  UUID NOT NULL REFERENCES payment_processors (id),
    method        VARCHAR(16) NOT NULL
);

CREATE UNIQUE INDEX idx_payment_processor_methods_processor_method ON payment_processor_methods (processor_id, method);

INSERT INTO payment_processor_methods (id, processor_id, method)
SELECT gen_random_uuid(), id, 'TRANSFER' FROM payment_processors;

-- A business's own ON/OFF setting for a processor. Only exceptions to the
-- processor's default_enabled are stored; no row = follow the default.
CREATE TABLE business_payment_processors (
    id            UUID PRIMARY KEY,
    status        VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    deleted_at    TIMESTAMPTZ,
    business_id   UUID NOT NULL REFERENCES business (id),
    processor_id  UUID NOT NULL REFERENCES payment_processors (id),
    enabled       BOOLEAN NOT NULL
);

CREATE UNIQUE INDEX idx_business_payment_processors_business_processor
    ON business_payment_processors (business_id, processor_id);
CREATE INDEX idx_business_payment_processors_processor ON business_payment_processors (processor_id);

-- The payment method each collection used. NULL for peer-to-peer transfers,
-- which never go through a processor; existing collections were transfers.
ALTER TABLE transactions ADD COLUMN payment_method VARCHAR(16);
UPDATE transactions SET payment_method = 'TRANSFER' WHERE payment_processor_id IS NOT NULL;

-- Business suspension (FR-Admin-6): why and by whom status last changed.
ALTER TABLE business
    ADD COLUMN status_reason      VARCHAR(512),
    ADD COLUMN status_changed_at  TIMESTAMPTZ,
    ADD COLUMN status_changed_by  UUID REFERENCES users (id);

-- Permissions: platform-* like every other staff capability (granted to no
-- business role; SUPERADMIN passes every check). The Supply Admin role keeps
-- its processor access under the new names; the old processors:* names go.
INSERT INTO permissions (id, name, resource, action) VALUES
    (gen_random_uuid(), 'platform-processors:read', 'platform-processors', 'read'),
    (gen_random_uuid(), 'platform-processors:manage', 'platform-processors', 'manage'),
    (gen_random_uuid(), 'platform-businesses:manage', 'platform-businesses', 'manage');

INSERT INTO role_permission (id, role_id, permission_id)
SELECT gen_random_uuid(), r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('platform-processors:read', 'platform-processors:manage')
WHERE r.name = 'SUPPLY_ADMIN' AND r.business_id IS NULL;

DELETE FROM role_permission
WHERE permission_id IN (SELECT id FROM permissions WHERE name IN ('processors:configure', 'processors:read'));
DELETE FROM permissions WHERE name IN ('processors:configure', 'processors:read');
