-- New permissions for settlement accounts, API keys, payment links, and
-- dynamic/temporary accounts, extending V0015's baseline catalog.
-- collection-account:manage is deliberately NOT assigned to any role here -
-- only SUPERADMIN's hard-coded bypass in PermissionChecker can reach it
-- (doc 3 §2.5). collect:create/withdraw:create are not seeded at all: they
-- are synthesized directly onto the API-key authentication principal by
-- ApiKeyAuthenticationFilter, never resolved via these role tables.

INSERT INTO permissions (id, name, resource, action) VALUES
    (gen_random_uuid(), 'settlements:read', 'settlements', 'read'),
    (gen_random_uuid(), 'settlements:manage', 'settlements', 'manage'),
    (gen_random_uuid(), 'apikeys:manage', 'apikeys', 'manage'),
    (gen_random_uuid(), 'paymentlinks:manage', 'paymentlinks', 'manage'),
    (gen_random_uuid(), 'paymentlinks:read', 'paymentlinks', 'read'),
    (gen_random_uuid(), 'collection-account:manage', 'collection-account', 'manage'),
    (gen_random_uuid(), 'temporaryaccounts:manage', 'temporaryaccounts', 'manage');

INSERT INTO role_permission (id, role_id, permission_id)
SELECT gen_random_uuid(), r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN (
    'settlements:read', 'settlements:manage', 'apikeys:manage',
    'paymentlinks:manage', 'paymentlinks:read', 'temporaryaccounts:manage'
)
WHERE r.name = 'BUSINESS_OWNER' AND r.business_id IS NULL;
