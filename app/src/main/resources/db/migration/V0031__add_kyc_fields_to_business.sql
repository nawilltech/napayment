-- FR-8 business KYB: the fields collected by the onboarding "business
-- details" step, plus the KYC review status/timestamp advanced by the
-- "submit for review" step. All nullable/defaulted - a business that
-- hasn't started KYC yet still has a valid row.
ALTER TABLE business
    ADD COLUMN business_type          VARCHAR(32),
    ADD COLUMN industry                VARCHAR(128),
    ADD COLUMN country_id              UUID REFERENCES countries (id),
    ADD COLUMN state_id                UUID REFERENCES states (id),
    ADD COLUMN address_line            VARCHAR(256),
    ADD COLUMN kyc_details_updated_at  TIMESTAMPTZ,
    ADD COLUMN kyc_status              VARCHAR(16) NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN kyc_submitted_at        TIMESTAMPTZ;
