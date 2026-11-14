-- Prilog za kalendar uz potvrdu rezervacije. Kao i tijelo, postoji samo dok obavijest čeka slanje.
ALTER TABLE notifications ADD COLUMN ics TEXT;
ALTER TABLE notifications ADD CONSTRAINT notifications_prilog_dok_ceka CHECK (status = 'PENDING' OR ics IS NULL);
