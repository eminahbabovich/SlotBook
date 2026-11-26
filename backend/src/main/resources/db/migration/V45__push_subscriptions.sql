-- Pretplata jednog browsera na push obavijesti. endpoint je adresa push servisa (Google, Mozilla,
-- Apple, Microsoft) za baš taj browser, a p256dh i auth su ključevi kojima se poruka šifruje za njega.
-- Isti browser ima jedan endpoint, pa ako se na njemu prijavi neko drugi, pretplata prelazi na njega.
CREATE TABLE push_subscriptions (
    id         UUID          PRIMARY KEY,
    user_id    UUID          NOT NULL REFERENCES users (id),
    endpoint   VARCHAR(1000) NOT NULL UNIQUE,
    p256dh     VARCHAR(200)  NOT NULL,
    auth       VARCHAR(100)  NOT NULL,
    device     VARCHAR(100),
    created_at TIMESTAMPTZ   NOT NULL
);

CREATE INDEX push_subscriptions_korisnik ON push_subscriptions (user_id);

-- Push je drugi kanal istog outboxa: isti red čekanja i isti ponovni pokušaji kao mail. Kad push servis
-- javi 404 ili 410, pretplata se briše, a s njom i njene neposlane obavijesti.
ALTER TABLE notifications ADD COLUMN channel VARCHAR(10) NOT NULL DEFAULT 'EMAIL';
ALTER TABLE notifications ADD COLUMN push_subscription_id UUID REFERENCES push_subscriptions (id) ON DELETE CASCADE;
ALTER TABLE notifications ADD CONSTRAINT notifications_kanal CHECK (
    channel IN ('EMAIL', 'PUSH') AND (channel = 'PUSH') = (push_subscription_id IS NOT NULL));
