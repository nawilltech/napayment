-- Platform admin registering settlement bank accounts on a business's behalf
-- (AdminBankAccountController). Like the other platform-* permissions
-- (V0044), granted to no seeded role: SUPERADMIN passes every check, ADMIN
-- staff get it only through an explicitly assigned role.
INSERT INTO permissions (id, name, resource, action) VALUES
    (gen_random_uuid(), 'platform-bank-accounts:manage', 'platform-bank-accounts', 'manage');
