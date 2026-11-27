-- Prijedlog iz chata. Model ga upiše alatom (create_booking, cancel_booking), a rezervacija ili
-- otkazivanje se desi tek kad korisnik pritisne Potvrdi. Model sam ne mijenja nijednu rezervaciju.
-- Korisnik i organizacija se čitaju preko sesije, pa prijedlog nestaje s njom.
CREATE TABLE chat_proposals (
    id              UUID         PRIMARY KEY,
    session_id      UUID         NOT NULL REFERENCES chat_sessions (id) ON DELETE CASCADE,
    action          VARCHAR(10)  NOT NULL,
    resource_id     UUID         REFERENCES resources (id),
    start_time      TIMESTAMPTZ,
    -- Kod otkazivanja rezervacija koja se otkazuje, kod rezervacije ona koja je nastala potvrdom
    booking_id      UUID         REFERENCES bookings (id) ON DELETE CASCADE,
    -- Ide u BookingService kao Idempotency-Key, pa dvije potvrde istog prijedloga daju jednu rezervaciju
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ  NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    confirmed_at    TIMESTAMPTZ,
    dismissed_at    TIMESTAMPTZ,

    CONSTRAINT chat_proposals_oblik CHECK (
        (action = 'BOOK' AND resource_id IS NOT NULL AND start_time IS NOT NULL)
        OR (action = 'CANCEL' AND booking_id IS NOT NULL AND resource_id IS NULL AND start_time IS NULL)),
    CONSTRAINT chat_proposals_jedan_ishod CHECK (confirmed_at IS NULL OR dismissed_at IS NULL)
);

CREATE INDEX chat_proposals_sesija ON chat_proposals (session_id);
