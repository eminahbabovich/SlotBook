-- Statistika bira rezervacije po resursu i periodu, u svim statusima. Indeks iza EXCLUDE pokriva samo
-- PENDING i CONFIRMED, pa je Postgres bez ovoga čitao cijelu tabelu, rezervacije svih organizacija,
-- i za statistiku jedne čitaonice za jedan mjesec. Mjerenje je u docs/PERFORMANSE.md.
CREATE INDEX bookings_resource_start ON bookings (resource_id, start_time);
