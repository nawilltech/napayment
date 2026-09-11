-- FR-8: the 4 required KYC document uploads per business. Unique on
-- (business_id, document_type) - a re-upload of the same type replaces the
-- previous row (and its stored file) rather than accumulating duplicates,
-- matching the onboarding UI's "one slot per required document" model.
CREATE TABLE kyc_documents (
    id             UUID PRIMARY KEY,
    status         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     UUID,
    deleted_at     TIMESTAMPTZ,
    business_id    UUID NOT NULL REFERENCES business (id),
    document_type  VARCHAR(32) NOT NULL,
    file_name      VARCHAR(256) NOT NULL,
    content_type   VARCHAR(128) NOT NULL,
    size_bytes     BIGINT NOT NULL,
    storage_key    VARCHAR(512) NOT NULL
);

CREATE UNIQUE INDEX idx_kyc_documents_business_id_document_type ON kyc_documents (business_id, document_type);
