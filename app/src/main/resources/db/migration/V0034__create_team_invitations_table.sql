-- FR-5a: a business invites a teammate under a specific role. token backs
-- the invite link (/invite/<token> on the frontend) and the accept-invite
-- signup endpoint; unique so a token can't be guessed/reused across invites.
CREATE TABLE team_invitations (
    id            UUID PRIMARY KEY,
    status        VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    deleted_at    TIMESTAMPTZ,
    business_id   UUID NOT NULL REFERENCES business (id),
    email         VARCHAR(128) NOT NULL,
    role_id       UUID NOT NULL REFERENCES roles (id),
    invitation_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    token         VARCHAR(64) NOT NULL,
    message       VARCHAR(280),
    invited_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at  TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_team_invitations_token ON team_invitations (token);
CREATE INDEX idx_team_invitations_business_id ON team_invitations (business_id);
