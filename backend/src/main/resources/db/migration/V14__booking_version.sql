-- Broj verzije za optimistično zaključavanje (@Version). Hibernate ga poveća pri svakoj izmjeni
-- i u UPDATE doda "where version = ?". Ako je neko drugi u međuvremenu promijenio red, UPDATE
-- ne nađe ništa i transakcija pada, umjesto da tiho prepiše tuđu promjenu.
ALTER TABLE bookings ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
