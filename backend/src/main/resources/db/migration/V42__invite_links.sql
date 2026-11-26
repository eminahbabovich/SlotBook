-- Pozivni link za plakat i grupu: svako ko ga otvori i ima potvrđen email ulazi kao klijent.
-- Baza čuva samo SHA-256 tokena. Token je HMAC nad id-jem (LinkToken), pa ga server izračuna ponovo.
CREATE TABLE invite_links (
    id              UUID        PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organizations (id),
    -- Uvijek klijent: link vidi svako ko prođe pored plakata, pa ne smije davati upravljanje
    role            VARCHAR(20) NOT NULL DEFAULT 'CLIENT',
    token_hash      VARCHAR(64) NOT NULL UNIQUE,
    max_uses        INT         NOT NULL,
    uses            INT         NOT NULL DEFAULT 0,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    created_by      UUID        REFERENCES users (id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL,

    CONSTRAINT invite_links_samo_klijent CHECK (role = 'CLIENT'),
    CONSTRAINT invite_links_najvise_mjesta CHECK (max_uses BETWEEN 1 AND 1000),
    -- Zadnja odbrana od previše ulazaka: i da Java pogriješi, baza ne pusti uses preko max_uses
    CONSTRAINT invite_links_ne_preko_granice CHECK (uses BETWEEN 0 AND max_uses),
    CONSTRAINT invite_links_rok_poslije_pravljenja CHECK (expires_at > created_at)
);

CREATE INDEX invite_links_organizacija ON invite_links (organization_id, created_at DESC);

ALTER TABLE audit_log DROP CONSTRAINT audit_log_radnja;
ALTER TABLE audit_log ADD CONSTRAINT audit_log_radnja CHECK (action IN ('ORGANIZATION_CREATED', 'INVITE_SENT',
    'INVITE_ACCEPTED', 'RESOURCE_CREATED', 'RESOURCE_UPDATED', 'RESOURCE_PROVIDER_CHANGED', 'RESOURCE_DEACTIVATED',
    'ACCOUNT_DELETED', 'INVITE_REVOKED', 'INVITE_LINK_CREATED', 'INVITE_LINK_REVOKED', 'INVITE_LINK_JOINED',
    'MEMBER_ROLE_CHANGED', 'MEMBER_REMOVED'));
