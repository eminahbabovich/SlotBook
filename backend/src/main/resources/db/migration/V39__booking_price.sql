-- Cijena prepisana s resursa u trenutku rezervacije. Ako admin poslije promijeni cijenu, klijent
-- i dalje plaća ono što je vidio kad je rezervisao. Postojeće rezervacije su besplatne.
ALTER TABLE bookings ADD COLUMN price_cents INTEGER NOT NULL DEFAULT 0;
ALTER TABLE bookings ADD CONSTRAINT bookings_cijena CHECK (price_cents BETWEEN 0 AND 100000);
