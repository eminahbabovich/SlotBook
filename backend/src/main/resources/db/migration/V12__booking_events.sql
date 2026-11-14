-- Historija statusa rezervacije. Red se upisuje u istoj transakciji kao promjena statusa,
-- pa ne može postojati promjena bez zapisa ni zapis bez promjene.
CREATE TABLE booking_events (
    id          UUID         PRIMARY KEY,
    booking_id  UUID         NOT NULL REFERENCES bookings (id),
    -- Prazno samo kod nastanka rezervacije
    from_status VARCHAR(20),
    to_status   VARCHAR(20)  NOT NULL,
    -- Ko je promijenio status: klijent, provider ili admin. Pozadinski posao (istek) dolazi u Fazi 5.
    actor_id    UUID         NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ  NOT NULL,

    CONSTRAINT booking_events_statusi CHECK (
        (from_status IS NULL OR from_status IN
            ('PENDING', 'CONFIRMED', 'REJECTED', 'EXPIRED', 'CANCELLED', 'COMPLETED', 'NO_SHOW'))
        AND to_status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'EXPIRED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')),
    -- Rezervacija nastaje samo kao PENDING ili CONFIRMED, vidi tabelu prelaza u PROJECT_PLAN.md
    CONSTRAINT booking_events_nastanak CHECK (from_status IS NOT NULL OR to_status IN ('PENDING', 'CONFIRMED'))
);

-- Historija jedne rezervacije, za GET /api/bookings/{id}/events
CREATE INDEX booking_events_booking_id ON booking_events (booking_id, created_at);
