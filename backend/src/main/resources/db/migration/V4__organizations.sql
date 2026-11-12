CREATE TABLE organizations (
    id         UUID PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    -- Dio adrese javnog profila, npr. /citaonica-fit
    slug       VARCHAR(60)  NOT NULL UNIQUE,
    -- IANA naziv zone (Europe/Sarajevo). Radno vrijeme resursa se računa u ovoj zoni.
    timezone   VARCHAR(64)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,

    CONSTRAINT organizations_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE TABLE organization_members (
    organization_id UUID        NOT NULL REFERENCES organizations (id),
    user_id         UUID        NOT NULL REFERENCES users (id),
    role            VARCHAR(20) NOT NULL,
    joined_at       TIMESTAMPTZ NOT NULL,

    -- Jedan red po paru: korisnik je u organizaciji najviše jednom, ali može biti u više organizacija
    PRIMARY KEY (organization_id, user_id),
    CONSTRAINT organization_members_role CHECK (role IN ('ADMIN', 'PROVIDER', 'CLIENT'))
);

-- PK pokriva pretragu po organizaciji, a "u kojim sam organizacijama" traži po korisniku
CREATE INDEX organization_members_user_id ON organization_members (user_id);
