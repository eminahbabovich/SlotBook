CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id),
    -- Svi tokeni nastali rotacijom iz jedne prijave. Kad se otkrije krađa, poništava se cijela porodica.
    family_id   UUID        NOT NULL,
    -- SHA-256 tokena, ne sam token: ko pročita bazu, ne može se s ovim prijaviti
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    replaced_by UUID REFERENCES refresh_tokens (id)
);

CREATE INDEX refresh_tokens_family_id ON refresh_tokens (family_id);
CREATE INDEX refresh_tokens_user_id ON refresh_tokens (user_id);
