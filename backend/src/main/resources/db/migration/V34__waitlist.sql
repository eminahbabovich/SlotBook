-- Lista čekanja za zauzet termin. Kad se termin oslobodi, posao ga ponudi prvom (OFFERED) do offered_until.
CREATE TABLE waitlist_entries (
    id            UUID        PRIMARY KEY,
    resource_id   UUID        NOT NULL REFERENCES resources (id),
    client_id     UUID        NOT NULL REFERENCES users (id),
    start_time    TIMESTAMPTZ NOT NULL,
    end_time      TIMESTAMPTZ NOT NULL,
    status        TEXT        NOT NULL,
    offered_until TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT waitlist_status CHECK (status IN ('WAITING', 'OFFERED', 'BOOKED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT waitlist_termin CHECK (start_time < end_time),
    -- Ponuda bez roka bi blokirala termin zauvijek
    CONSTRAINT waitlist_ponuda_ima_rok CHECK (status <> 'OFFERED' OR offered_until IS NOT NULL)
);

-- Jedan klijent je najviše jednom na listi za isti termin, dok je upis živ
CREATE UNIQUE INDEX waitlist_jednom_po_terminu ON waitlist_entries (resource_id, start_time, client_id)
    WHERE status IN ('WAITING', 'OFFERED');

-- Termin je u jednom trenutku ponuđen samo jednoj osobi. Ovo čuva baza i kad bi dva posla radila istovremeno.
CREATE UNIQUE INDEX waitlist_jedna_ponuda ON waitlist_entries (resource_id, start_time)
    WHERE status = 'OFFERED';

ALTER TABLE notifications DROP CONSTRAINT notifications_type;
ALTER TABLE notifications ADD CONSTRAINT notifications_type CHECK (type IN ('BOOKING_AWAITS_CONFIRMATION',
    'BOOKING_CONFIRMED', 'BOOKING_REJECTED', 'BOOKING_CANCELLED', 'BOOKING_EXPIRED', 'BOOKING_REMINDER',
    'EMAIL_VERIFICATION', 'PASSWORD_RESET', 'ORGANIZATION_INVITE', 'ACCOUNT_EXISTS', 'WAITLIST_OFFER'));
