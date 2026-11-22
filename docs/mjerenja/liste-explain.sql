-- EXPLAIN za upite iza kalendara slobodnih termina, rasporeda providera i "mojih rezervacija"
-- (vidi docs/PERFORMANSE.md). SQL je onaj koji Hibernate stvarno pošalje, uhvaćen u
-- UpitiPoZahtjevuApiTest, s vrijednostima umjesto parametara. Podaci su iz statistika-podaci.sql.

ANALYZE;
SELECT r.id AS sto FROM resources r JOIN organizations o ON o.id = r.organization_id
WHERE o.slug = 'citaonica-1' ORDER BY r.id LIMIT 1 \gset
SELECT client_id AS klijent FROM bookings GROUP BY client_id ORDER BY count(*) DESC, client_id LIMIT 1 \gset
SELECT count(*) AS rezervacija_klijenta FROM bookings WHERE client_id = :'klijent';

\echo '=== zauzeti termini (FreeSlotService), sedmica jednog stola'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
select b1_0.id,b1_0.client_id,b1_0.created_at,b1_0.end_time,b1_0.expires_at,b1_0.idempotency_key,b1_0.resource_id,
b1_0.start_time,b1_0.status,b1_0.version from bookings b1_0
where b1_0.resource_id=:'sto' and b1_0.status in ('PENDING','CONFIRMED')
and b1_0.start_time<'2035-11-26 00:00+01' and b1_0.end_time>'2035-11-19 00:00+01';

\echo '=== raspored providera prije V33, bez donje granice'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
select b1_0.id,b1_0.client_id,c1_0.id,c1_0.created_at,c1_0.display_name,c1_0.email,c1_0.email_verified_at,
c1_0.password_hash,b1_0.created_at,b1_0.end_time,b1_0.expires_at,b1_0.idempotency_key,b1_0.resource_id,
b1_0.start_time,b1_0.status,b1_0.version from bookings b1_0 join users c1_0 on c1_0.id=b1_0.client_id
where b1_0.resource_id=:'sto' and b1_0.start_time<'2035-11-26 00:00+01' and b1_0.end_time>'2035-11-19 00:00+01'
order by b1_0.start_time,b1_0.id;

\echo '=== raspored providera (findSchedule) od V33, s donjom granicom 8 sati prije perioda'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
select b1_0.id,b1_0.client_id,c1_0.id,c1_0.created_at,c1_0.display_name,c1_0.email,c1_0.email_verified_at,
c1_0.password_hash,b1_0.created_at,b1_0.end_time,b1_0.expires_at,b1_0.idempotency_key,b1_0.resource_id,
b1_0.start_time,b1_0.status,b1_0.version from bookings b1_0 join users c1_0 on c1_0.id=b1_0.client_id
where b1_0.resource_id=:'sto' and b1_0.start_time>'2035-11-18 16:00+01' and b1_0.start_time<'2035-11-26 00:00+01'
and b1_0.end_time>'2035-11-19 00:00+01'
order by b1_0.start_time,b1_0.id;

\echo '=== moje rezervacije (findMine), prva stranica od 20, klijent s najviše rezervacija'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
select b1_0.id,b1_0.client_id,b1_0.created_at,b1_0.end_time,b1_0.expires_at,b1_0.idempotency_key,b1_0.resource_id,
r1_0.id,r1_0.active,r1_0.auto_confirm,r1_0.cancel_notice_hours,r1_0.created_at,r1_0.description,r1_0.image_key,
r1_0.name,r1_0.organization_id,o1_0.id,o1_0.created_at,o1_0.name,o1_0.slug,o1_0.timezone,r1_0.provider_id,
r1_0.slot_minutes,b1_0.start_time,b1_0.status,b1_0.version from bookings b1_0
join resources r1_0 on r1_0.id=b1_0.resource_id join organizations o1_0 on o1_0.id=r1_0.organization_id
where b1_0.client_id=:'klijent' order by b1_0.start_time desc,b1_0.id fetch first 20 rows only;

\echo '=== broj za stranicu (count iz Page), isti klijent'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
select count(b1_0.id) from bookings b1_0 where b1_0.client_id=:'klijent';
