-- Outbox: obavijest se upisuje u istoj transakciji kao promjena koja je izaziva, a šalje je
-- poseban posao kasnije. Ako promjena padne, nema ni obavijesti. Ako slanje padne, red ostaje
-- i pokušava se ponovo. Ništa se ne šalje iz transakcije rezervacije.
CREATE TABLE notifications (
    id              UUID        PRIMARY KEY,
    -- Kome ide. Adresa se uzima iz users pri slanju, pa promjena emaila važi i za neposlane.
    user_id         UUID        NOT NULL REFERENCES users (id),
    type            VARCHAR(40) NOT NULL,
    booking_id      UUID        REFERENCES bookings (id),
    status          VARCHAR(20) NOT NULL,
    attempts        INT         NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    sent_at         TIMESTAMPTZ,

    CONSTRAINT notifications_type CHECK (type IN ('BOOKING_AWAITS_CONFIRMATION', 'BOOKING_CONFIRMED',
        'BOOKING_REJECTED', 'BOOKING_CANCELLED', 'BOOKING_EXPIRED')),
    CONSTRAINT notifications_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    -- Obavijest o rezervaciji bez rezervacije se ne bi imala šta poslati
    CONSTRAINT notifications_rezervacija CHECK (type NOT LIKE 'BOOKING\_%' OR booking_id IS NOT NULL),
    CONSTRAINT notifications_poslana CHECK ((status = 'SENT') = (sent_at IS NOT NULL))
);
