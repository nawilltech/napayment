-- Permissions for the new onboarding/KYC surface: business details, owner
-- identity, KYC documents/submit (business:kyc-manage), and contact settings
-- (business:manage). Webhook config reuses the existing apikeys:manage; team
-- invitations reuse the existing roles:manage - both already assigned to
-- BUSINESS_OWNER by earlier migrations.

INSERT INTO permissions (id, name, resource, action) VALUES
    (gen_random_uuid(), 'business:kyc-manage', 'business', 'kyc-manage'),
    (gen_random_uuid(), 'business:manage', 'business', 'manage');

INSERT INTO role_permission (id, role_id, permission_id)
SELECT gen_random_uuid(), r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('business:kyc-manage', 'business:manage')
WHERE r.name = 'BUSINESS_OWNER' AND r.business_id IS NULL;
