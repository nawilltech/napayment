-- Nigeria's administrative hierarchy: 1 = State, 2 = Local Government Area, 3 = Ward (doc 1 §5).
CREATE TABLE states (
    id          UUID PRIMARY KEY,
    status      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    deleted_at  TIMESTAMPTZ,
    country_id  UUID NOT NULL REFERENCES countries (id),
    name        VARCHAR(128) NOT NULL,
    level       INTEGER NOT NULL,
    parent_id   UUID REFERENCES states (id)
);

CREATE INDEX idx_states_country_id ON states (country_id);
CREATE INDEX idx_states_parent_id ON states (parent_id);
CREATE INDEX idx_states_country_id_level ON states (country_id, level);
