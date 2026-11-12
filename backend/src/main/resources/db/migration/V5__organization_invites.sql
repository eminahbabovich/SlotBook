CREATE TABLE organization_invites (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organizations (id),
    email           VARCHAR(254) NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    -- SHA-256 tokena, kao kod refresh tokena: ko pročita bazu, ne može prihvatiti tuđu pozivnicu
    token_hash      VARCHAR(64)  NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ  NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    accepted_at     TIMESTAMPTZ,

    CONSTRAINT organization_invites_email_mala_slova CHECK (email = lower(email)),
    CONSTRAINT organization_invites_role CHECK (role IN ('ADMIN', 'PROVIDER', 'CLIENT')),
    CONSTRAINT organization_invites_rok_poslije_slanja CHECK (expires_at > created_at)
);

-- Najviše jedna neprihvaćena pozivnica po emailu u organizaciji. Prihvaćene ostaju kao historija
-- i ne smetaju novoj, zato je indeks samo nad redovima gdje je accepted_at prazan.
CREATE UNIQUE INDEX organization_invites_jedna_otvorena
    ON organization_invites (organization_id, email)
    WHERE accepted_at IS NULL;
