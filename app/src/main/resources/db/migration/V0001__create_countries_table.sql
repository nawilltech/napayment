CREATE TABLE countries (
    id          UUID PRIMARY KEY,
    status      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    deleted_at  TIMESTAMPTZ,
    name        VARCHAR(128) NOT NULL,
    iso3        VARCHAR(3) NOT NULL,
    flag_url    VARCHAR(512),
    currency    VARCHAR(3) NOT NULL
);

CREATE UNIQUE INDEX idx_countries_iso3 ON countries (iso3);

-- MVP defaults to Naira (NGN) only (doc 1 FR-13); other African markets are v1.0 scope.
INSERT INTO countries (id, name, iso3, currency)
VALUES (gen_random_uuid(), 'Nigeria', 'NGA', 'NGN');
