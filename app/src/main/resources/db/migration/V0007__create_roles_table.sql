-- business_id NULL = platform-scoped role (e.g. USER, BUSINESS_OWNER seeded
-- in V0015). Business-created roles (FR-5a) always carry a businessId.
CREATE TABLE roles (
    id           UUID PRIMARY KEY,
    status       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   UUID,
    deleted_at   TIMESTAMPTZ,
    name         VARCHAR(64) NOT NULL,
    business_id  UUID REFERENCES business (id)
);

CREATE INDEX idx_roles_business_id ON roles (business_id);
-- One platform role per name (business_id IS NULL); business-scoped roles
-- may reuse names across different businesses.
CREATE UNIQUE INDEX idx_roles_platform_name ON roles (name) WHERE business_id IS NULL;
