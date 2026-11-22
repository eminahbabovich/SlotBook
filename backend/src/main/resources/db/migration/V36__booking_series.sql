-- Ponavljajuća rezervacija: isti termin svake sedmice, svaka sedmica je svoja rezervacija (svoj
-- status, otkazivanje i EXCLUDE), a series_id ih veže. Ponovljen zahtjev s istim Idempotency-Key
-- po njemu vrati cijelu seriju, a ne samo prvu sedmicu.
ALTER TABLE bookings ADD COLUMN series_id UUID;
CREATE INDEX bookings_series ON bookings (series_id) WHERE series_id IS NOT NULL;
