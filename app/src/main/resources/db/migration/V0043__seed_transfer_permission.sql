-- FR-Auth-1: peer-to-peer transfer between two Nawill virtual accounts.
-- Granted to both platform-default roles - a business's own virtual account
-- can send/receive a transfer the same as an individual user's.
INSERT INTO permissions (id, name, resource, action) VALUES
    (gen_random_uuid(), 'transfers:create', 'transfers', 'create');

INSERT INTO role_permission (id, role_id, permission_id)
SELECT gen_random_uuid(), r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'transfers:create'
WHERE r.name IN ('USER', 'BUSINESS_OWNER') AND r.business_id IS NULL;
