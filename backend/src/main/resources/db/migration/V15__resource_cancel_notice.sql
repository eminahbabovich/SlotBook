-- Koliko sati prije termina klijent najkasnije smije otkazati. 0 znači do samog početka.
-- U skici je ovo bila kolona min_notice_hours, ali iz imena se ne vidi da je rok za otkazivanje,
-- a ne za rezervaciju. Admin i provider resursa otkazuju bez roka.
ALTER TABLE resources ADD COLUMN cancel_notice_hours INTEGER NOT NULL DEFAULT 0;
-- Sedmica je gornja granica, da greška u unosu (npr. 2400 umjesto 24) ne zaključa sve rezervacije
ALTER TABLE resources ADD CONSTRAINT resources_cancel_notice_hours CHECK (cancel_notice_hours BETWEEN 0 AND 168);
