CREATE TABLE bookings (
    id          UUID         PRIMARY KEY,
    resource_id UUID         NOT NULL REFERENCES resources (id),
    -- Članstvo klijenta u organizaciji resursa provjerava servis pri rezervaciji. FK na
    -- organization_members bi spriječio izlazak iz organizacije dok postoje stare rezervacije.
    client_id   UUID         NOT NULL REFERENCES users (id),
    start_time  TIMESTAMPTZ  NOT NULL,
    end_time    TIMESTAMPTZ  NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,

    CONSTRAINT bookings_pocetak_prije_kraja CHECK (start_time < end_time),
    CONSTRAINT bookings_status CHECK (status IN
        ('PENDING', 'CONFIRMED', 'REJECTED', 'EXPIRED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')),
    -- Dvije aktivne rezervacije istog resursa se ne smiju preklapati, ni kad stignu u istom
    -- trenutku. Provjera u Javi bi u oba zahtjeva vidjela slobodan termin. Kraj je isključen,
    -- pa 09-10 i 10-11 mogu zajedno. Otkazana, odbijena i istekla ne drže termin, zato WHERE.
    CONSTRAINT bookings_bez_preklapanja EXCLUDE USING gist (
        resource_id WITH =,
        tstzrange(start_time, end_time) WITH &&
    ) WHERE (status IN ('PENDING', 'CONFIRMED'))
);
