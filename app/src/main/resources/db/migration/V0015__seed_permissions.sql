-- Baseline permission catalog + the platform roles assigned at signup
-- (AuthService) and to a future Supply Admin (FR-6). SUPERADMIN is
-- deliberately NOT assembled here - its access is hard-coded in
-- PermissionChecker (common-core), never from this table (doc 3 §2.2).

INSERT INTO permissions (id, name, resource, action) VALUES
    (gen_random_uuid(), 'transactions:create', 'transactions', 'create'),
    (gen_random_uuid(), 'transactions:read', 'transactions', 'read'),
    (gen_random_uuid(), 'virtualaccounts:read', 'virtualaccounts', 'read'),
    (gen_random_uuid(), 'processors:configure', 'processors', 'configure'),
    (gen_random_uuid(), 'processors:read', 'processors', 'read'),
    (gen_random_uuid(), 'roles:manage', 'roles', 'manage'),
    (gen_random_uuid(), 'users:read', 'users', 'read');

INSERT INTO roles (id, name, business_id) VALUES
    (gen_random_uuid(), 'USER', NULL),
    (gen_random_uuid(), 'BUSINESS_OWNER', NULL),
    (gen_random_uuid(), 'SUPPLY_ADMIN', NULL);

INSERT INTO role_permission (id, role_id, permission_id)
SELECT gen_random_uuid(), r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('transactions:create', 'transactions:read', 'virtualaccounts:read')
WHERE r.name = 'USER' AND r.business_id IS NULL;

INSERT INTO role_permission (id, role_id, permission_id)
SELECT gen_random_uuid(), r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('transactions:create', 'transactions:read', 'virtualaccounts:read', 'roles:manage')
WHERE r.name = 'BUSINESS_OWNER' AND r.business_id IS NULL;

INSERT INTO role_permission (id, role_id, permission_id)
SELECT gen_random_uuid(), r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('processors:configure', 'processors:read')
WHERE r.name = 'SUPPLY_ADMIN' AND r.business_id IS NULL;
