-- Grupni termin (npr. trening u sali s 5 mjesta). Kapacitet 1 je običan resurs, kao do sada.
ALTER TABLE resources ADD COLUMN capacity INTEGER NOT NULL DEFAULT 1;
ALTER TABLE resources ADD CONSTRAINT resources_kapacitet CHECK (capacity BETWEEN 1 AND 100);

-- Svaka rezervacija drži jedno mjesto, od 1 do kapaciteta resursa. Postojeće su sve na mjestu 1,
-- pa za njih novi constraint znači isto što i stari.
ALTER TABLE bookings ADD COLUMN seat INTEGER NOT NULL DEFAULT 1;
ALTER TABLE bookings ADD CONSTRAINT bookings_mjesto CHECK (seat >= 1);

-- Umjesto "resurs se ne preklapa" sada važi "isto mjesto se ne preklapa". Ako je svako mjesto
-- najviše kapacitet, u istom trenutku ne može biti više rezervacija od mjesta. Da mjesto nije
-- veće od kapaciteta provjerava Java pod lockom resursa, jer CHECK ne može čitati drugu tabelu.
ALTER TABLE bookings DROP CONSTRAINT bookings_bez_preklapanja;
ALTER TABLE bookings ADD CONSTRAINT bookings_mjesto_bez_preklapanja EXCLUDE USING gist (
    resource_id WITH =,
    seat WITH =,
    tstzrange(start_time, end_time) WITH &&
) WHERE (status IN ('PENDING', 'CONFIRMED'));

-- Jedna osoba drži najviše jedno mjesto u istom terminu. Kod kapaciteta 1 je to već važilo.
ALTER TABLE bookings ADD CONSTRAINT bookings_klijent_jednom EXCLUDE USING gist (
    resource_id WITH =,
    client_id WITH =,
    tstzrange(start_time, end_time) WITH &&
) WHERE (status IN ('PENDING', 'CONFIRMED'));
