-- FR-3: platform review of submitted business KYC. Who decided, when, and
-- (for a rejection) why - shown back to the business so they can fix and
-- resubmit. Cleared again on resubmission (Business#submitKycForReview).
ALTER TABLE business
    ADD COLUMN kyc_reviewed_at  TIMESTAMPTZ,
    ADD COLUMN kyc_reviewed_by  UUID REFERENCES users (id),
    ADD COLUMN kyc_review_note  VARCHAR(512);

CREATE INDEX idx_business_kyc_status ON business (kyc_status);

-- Platform-level (cross-business) permissions for the admin console.
-- Deliberately not granted to any seeded role: SUPERADMIN passes every
-- @auth.can check (PermissionChecker), and ADMIN staff get them only through
-- an explicitly assigned role.
INSERT INTO permissions (id, name, resource, action) VALUES
    (gen_random_uuid(), 'platform-businesses:read', 'platform-businesses', 'read'),
    (gen_random_uuid(), 'platform-kyc:review', 'platform-kyc', 'review'),
    (gen_random_uuid(), 'platform-audit:read', 'platform-audit', 'read');
