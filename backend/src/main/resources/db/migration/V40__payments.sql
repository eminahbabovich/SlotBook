-- Jedan pokušaj plaćanja rezervacije, tj. jedna Stripe Checkout sesija. Rezervacija i uplata su
-- odvojene: rezervacija kaže ko drži termin, a uplata šta se desilo s novcem.
CREATE TABLE payments (
    id                UUID         PRIMARY KEY,
    booking_id        UUID         NOT NULL REFERENCES bookings (id),
    amount_cents      INTEGER      NOT NULL,
    currency          VARCHAR(3)   NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    stripe_session_id VARCHAR(255) NOT NULL,
    checkout_url      TEXT         NOT NULL,
    expires_at        TIMESTAMPTZ  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT payments_iznos CHECK (amount_cents > 0),
    CONSTRAINT payments_status CHECK (status IN ('OPEN', 'PAID', 'EXPIRED', 'REFUNDED')),
    -- Webhook (korak 3) uplatu nalazi po sesiji, pa jedna sesija ne smije pripadati dvjema uplatama
    CONSTRAINT payments_sesija UNIQUE (stripe_session_id)
);

-- Dupli klik na "Plati" ne smije otvoriti dvije sesije za istu rezervaciju, pa ni dvije naplate
CREATE UNIQUE INDEX payments_jedna_otvorena ON payments (booking_id) WHERE status = 'OPEN';
