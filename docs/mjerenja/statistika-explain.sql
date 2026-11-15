-- EXPLAIN za upite statistike, za jednu čitaonicu (10 stolova od 200). Upiti su isti kao u
-- StatsRepository. Za JPQL je uzet SQL koji Hibernate stvarno pošalje (spring.jpa.show-sql).

SELECT id AS org FROM organizations WHERE slug = 'citaonica-1' \gset
SELECT user_id AS provider FROM organization_members WHERE organization_id = :'org' ORDER BY user_id LIMIT 1 \gset

\echo '=== countByStatus, novembar'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
select b1_0.status,count(b1_0.id) from bookings b1_0 join resources r1_0 on r1_0.id=b1_0.resource_id
where r1_0.organization_id = :'org' and b1_0.start_time >= '2035-11-01 00:00+01' and b1_0.start_time < '2035-12-01 00:00+01'
group by b1_0.status;

\echo '=== countByStatus, cijela 2035.'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
select b1_0.status,count(b1_0.id) from bookings b1_0 join resources r1_0 on r1_0.id=b1_0.resource_id
where r1_0.organization_id = :'org' and b1_0.start_time >= '2035-01-01 00:00+01' and b1_0.start_time < '2036-01-01 00:00+01'
group by b1_0.status;

\echo '=== countByStatusForProvider, novembar'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
select b1_0.status,count(b1_0.id) from bookings b1_0 join resources r1_0 on r1_0.id=b1_0.resource_id
where r1_0.organization_id = :'org' and r1_0.provider_id = :'provider'
	and b1_0.start_time >= '2035-11-01 00:00+01' and b1_0.start_time < '2035-12-01 00:00+01'
group by b1_0.status;

\echo '=== countByDayAndHour, novembar'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT CAST(extract(isodow FROM b.start_time AT TIME ZONE o.timezone) AS integer) AS dan,
	CAST(extract(hour FROM b.start_time AT TIME ZONE o.timezone) AS integer) AS sat,
	count(*) AS broj
FROM bookings b
JOIN resources r ON r.id = b.resource_id
JOIN organizations o ON o.id = r.organization_id
WHERE r.organization_id = :'org'
	AND b.start_time >= '2035-11-01 00:00+01' AND b.start_time < '2035-12-01 00:00+01'
GROUP BY dan, sat
ORDER BY dan, sat;

\echo '=== bookedMinutes, cijela 2035.'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT b.resource_id, CAST(sum(extract(epoch FROM b.end_time - b.start_time)) / 60 AS bigint)
FROM bookings b
JOIN resources r ON r.id = b.resource_id
WHERE r.organization_id = :'org'
	AND b.status IN ('CONFIRMED', 'COMPLETED', 'NO_SHOW')
	AND b.start_time >= '2035-01-01 00:00+01' AND b.start_time < '2036-01-01 00:00+01'
GROUP BY b.resource_id;
