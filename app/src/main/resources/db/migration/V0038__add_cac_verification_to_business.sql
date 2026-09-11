-- Tracks the outcome of the best-effort CAC public-registry lookup run when
-- business KYC details are saved (CacLookupGateway). Defaults to
-- unverified/unset for existing rows and any lookup that couldn't confirm a
-- match - this is a signal for the admin KYC review queue (FR-3), not a
-- gate on saving business details.
ALTER TABLE business
    ADD COLUMN cac_verified        BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN cac_verified_name   VARCHAR(256),
    ADD COLUMN cac_verification_source VARCHAR(32);
