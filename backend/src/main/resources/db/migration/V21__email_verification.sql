ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;

-- Jednokratni tokeni iz emaila. Kao kod pozivnica, baza čuva samo SHA-256, a token je samo u linku.
CREATE TABLE user_tokens (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id),
    type       VARCHAR(20) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,

    CONSTRAINT user_tokens_type CHECK (type IN ('EMAIL_VERIFY')),
    CONSTRAINT user_tokens_rok_poslije_nastanka CHECK (expires_at > created_at)
);

ALTER TABLE notifications DROP CONSTRAINT notifications_type;
ALTER TABLE notifications ADD CONSTRAINT notifications_type CHECK (type IN ('BOOKING_AWAITS_CONFIRMATION',
    'BOOKING_CONFIRMED', 'BOOKING_REJECTED', 'BOOKING_CANCELLED', 'BOOKING_EXPIRED', 'EMAIL_VERIFICATION'));

-- Tijelo maila za potvrdu emaila nosi token u linku. Da ne ostane u bazi, tijelo postoji samo dok
-- obavijest čeka slanje, za sve obavijesti, pa i za one o rezervaciji (manje ličnih podataka u bazi).
UPDATE notifications SET body = NULL WHERE status <> 'PENDING';
ALTER TABLE notifications ALTER COLUMN body DROP NOT NULL;
ALTER TABLE notifications ADD CONSTRAINT notifications_tijelo_dok_ceka
    CHECK ((status = 'PENDING') = (body IS NOT NULL));
