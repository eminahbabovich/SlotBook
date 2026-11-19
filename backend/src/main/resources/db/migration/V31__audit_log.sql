-- Ko je u organizaciji uradio osjetljivu radnju i kada. Upisuje se u istoj transakciji kao radnja.
CREATE TABLE audit_log (
    -- Redni broj, a ne UUID: više zapisa u istoj milisekundi i dalje ima tačan redoslijed
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organizations (id),
    -- Brisanje naloga (Faza 8, korak 9) ne briše historiju organizacije, samo ime iz nje
    actor_id        UUID        REFERENCES users (id) ON DELETE SET NULL,
    action          VARCHAR(40) NOT NULL,
    -- Bez stranog ključa: zapis ostaje i kad pozivnica ili resurs nestanu
    target_id       UUID,
    detail          VARCHAR(200),
    created_at      TIMESTAMPTZ NOT NULL,

    CONSTRAINT audit_log_radnja CHECK (action IN ('ORGANIZATION_CREATED', 'INVITE_SENT', 'INVITE_ACCEPTED',
        'RESOURCE_CREATED', 'RESOURCE_UPDATED', 'RESOURCE_PROVIDER_CHANGED', 'RESOURCE_DEACTIVATED'))
);

CREATE INDEX audit_log_organizacija ON audit_log (organization_id, id DESC);
