-- Nepotvrđena rezervacija drži termin samo do roka. Poslije roka je pozadinski posao prebaci
-- u EXPIRED i termin se oslobodi. Rok se ne mijenja, ni kad provider potvrdi.
ALTER TABLE bookings ADD COLUMN expires_at TIMESTAMPTZ;

-- Postojeće PENDING rezervacije dobiju isto pravilo kao nove: 24 sata od nastanka, najkasnije početak
UPDATE bookings SET expires_at = LEAST(created_at + interval '24 hours', start_time) WHERE status = 'PENDING';

-- PENDING bez roka nikad ne bi istekla i držala bi termin dok neko ne reaguje
ALTER TABLE bookings ADD CONSTRAINT bookings_pending_ima_rok
    CHECK (status <> 'PENDING' OR expires_at IS NOT NULL);
-- Rok poslije početka nema smisla: termin bi prošao, a rezervacija i dalje čekala potvrdu
ALTER TABLE bookings ADD CONSTRAINT bookings_rok_do_pocetka
    CHECK (expires_at IS NULL OR expires_at <= start_time);
