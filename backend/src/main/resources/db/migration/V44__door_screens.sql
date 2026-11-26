-- Ekran na vratima: tajni link za tablet ili TV koji pokazuje današnje termine bez prijave.
-- Za cijelu organizaciju (resource_id NULL) ili jedan resurs. Kao kod pozivnog linka, baza čuva samo
-- SHA-256 tokena, a token je HMAC nad id-jem (LinkToken, svrha EKRAN).
-- Za strani ključ ispod: resurs ekrana je baš iz organizacije ekrana
ALTER TABLE resources ADD CONSTRAINT resources_id_organizacija UNIQUE (id, organization_id);

CREATE TABLE door_screens (
    id              UUID        PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organizations (id),
    resource_id     UUID,
    token_hash      VARCHAR(64) NOT NULL UNIQUE,
    revoked_at      TIMESTAMPTZ,
    created_by      UUID        REFERENCES users (id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL,

    -- Resurs ekrana mora biti iz iste organizacije, i to čuva baza, ne samo servis
    CONSTRAINT door_screens_resurs_iz_organizacije FOREIGN KEY (resource_id, organization_id)
        REFERENCES resources (id, organization_id)
);

-- Novi ekran za isto mjesto zamjenjuje stari: živ je najviše jedan po organizaciji i resursu.
-- COALESCE, jer bi s NULL-om svaki ekran cijele organizacije bio "različit" od drugog.
CREATE UNIQUE INDEX door_screens_jedan_zivi ON door_screens
    (organization_id, COALESCE(resource_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE revoked_at IS NULL;

ALTER TABLE audit_log DROP CONSTRAINT audit_log_radnja;
ALTER TABLE audit_log ADD CONSTRAINT audit_log_radnja CHECK (action IN ('ORGANIZATION_CREATED', 'INVITE_SENT',
    'INVITE_ACCEPTED', 'RESOURCE_CREATED', 'RESOURCE_UPDATED', 'RESOURCE_PROVIDER_CHANGED', 'RESOURCE_DEACTIVATED',
    'ACCOUNT_DELETED', 'INVITE_REVOKED', 'INVITE_LINK_CREATED', 'INVITE_LINK_REVOKED', 'INVITE_LINK_JOINED',
    'MEMBER_ROLE_CHANGED', 'MEMBER_REMOVED', 'ORGANIZATION_RULES_CHANGED', 'DOOR_SCREEN_CREATED',
    'DOOR_SCREEN_REVOKED'));
