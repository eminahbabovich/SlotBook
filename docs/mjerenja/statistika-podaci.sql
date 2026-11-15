-- Podaci za mjerenje upita statistike (vidi docs/PERFORMANSE.md). Pokreće se nad praznom šemom
-- iz Flyway migracija u privremenom kontejneru, nikad nad bazom s pravim podacima.
--
-- 20 čitaonica, u svakoj 2 providera s po 5 stolova. Svaki sto kroz cijelu 2035. ima satne
-- termine od 8 do 16, a oko 70 % ih je rezervisano: otprilike 400 hiljada rezervacija.

SELECT setseed(0.42);

INSERT INTO users (id, email, password_hash, display_name, created_at)
SELECT gen_random_uuid(), 'korisnik' || i || '@example.com', 'hash', 'Korisnik ' || i, now()
FROM generate_series(1, 2000) AS i;

INSERT INTO organizations (id, name, slug, timezone, created_at)
SELECT gen_random_uuid(), 'Čitaonica ' || i, 'citaonica-' || i, 'Europe/Sarajevo', now()
FROM generate_series(1, 20) AS i;

INSERT INTO organization_members (organization_id, user_id, role, joined_at)
SELECT o.id, u.id, 'PROVIDER', now()
FROM (SELECT id, row_number() OVER (ORDER BY slug) AS n FROM organizations) AS o
JOIN (SELECT id, row_number() OVER (ORDER BY email) AS n FROM users) AS u ON u.n IN (o.n * 2 - 1, o.n * 2);

INSERT INTO resources (id, organization_id, provider_id, name, slot_minutes, created_at)
SELECT gen_random_uuid(), m.organization_id, m.user_id, 'Sto br. ' || s, 60, now()
FROM organization_members AS m, generate_series(1, 5) AS s;

-- Raspodjela statusa otprilike kao u čitaonici kroz godinu: većina završena, svaka deseta otkazana
WITH klijenti AS (SELECT array_agg(id) AS ids FROM users),
termini AS (
	SELECT r.id AS resource_id,
		(dan + make_interval(hours => sat)) AT TIME ZONE 'Europe/Sarajevo' AS pocetak,
		random() AS s
	FROM resources AS r,
		generate_series(timestamp '2035-01-01', timestamp '2035-12-31', interval '1 day') AS dan,
		generate_series(8, 15) AS sat
	WHERE random() < 0.7)
INSERT INTO bookings (id, resource_id, client_id, start_time, end_time, status, created_at, expires_at)
SELECT gen_random_uuid(), t.resource_id, k.ids[1 + floor(random() * 2000)::int], t.pocetak,
	t.pocetak + interval '1 hour',
	CASE WHEN t.s < 0.60 THEN 'COMPLETED' WHEN t.s < 0.70 THEN 'CONFIRMED' WHEN t.s < 0.80 THEN 'CANCELLED'
		WHEN t.s < 0.85 THEN 'NO_SHOW' WHEN t.s < 0.90 THEN 'EXPIRED' WHEN t.s < 0.95 THEN 'REJECTED'
		ELSE 'PENDING' END,
	t.pocetak - interval '1 day', t.pocetak
FROM termini AS t, klijenti AS k;

ANALYZE;

SELECT count(*) AS rezervacija, pg_size_pretty(pg_total_relation_size('bookings')) AS velicina FROM bookings;
