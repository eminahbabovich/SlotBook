-- Podaci za k6 load test (vidi docs/PERFORMANSE.md). Pokreće se nad praznom šemom iz Flyway
-- migracija u privremenoj bazi, nikad nad bazom s pravim podacima.
--
-- Teretana s 20 sala za obične rezervacije i jednom salom za sudar, admin i 100 članova.
-- Svi imaju lozinku opterecenje-bjelave-2035 (BCrypt snage 10, kao u aplikaciji) i potvrđen email.

INSERT INTO users (id, email, password_hash, display_name, created_at, email_verified_at)
VALUES ('00000000-0000-0000-0000-00000000a001', 'trener.k6@example.com',
	'$2a$10$M9VVesVapJLKAcH5Gz7Xyukd191jVxHkZLROlnWmRKre0IbOBvXIW', 'Trener Edin', now(), now());

INSERT INTO users (id, email, password_hash, display_name, created_at, email_verified_at)
SELECT gen_random_uuid(), 'clan' || i || '.k6@example.com',
	'$2a$10$M9VVesVapJLKAcH5Gz7Xyukd191jVxHkZLROlnWmRKre0IbOBvXIW', 'Član ' || i, now(), now()
FROM generate_series(1, 100) AS i;

INSERT INTO organizations (id, name, slug, timezone, created_at)
VALUES ('00000000-0000-0000-0000-00000000b001', 'Teretana Bjelave', 'teretana-k6', 'Europe/Sarajevo', now());

INSERT INTO organization_members (organization_id, user_id, role, joined_at)
VALUES ('00000000-0000-0000-0000-00000000b001', '00000000-0000-0000-0000-00000000a001', 'ADMIN', now());

INSERT INTO organization_members (organization_id, user_id, role, joined_at)
SELECT '00000000-0000-0000-0000-00000000b001', id, 'CLIENT', now()
FROM users WHERE email LIKE 'clan%.k6@example.com';

INSERT INTO resources (id, organization_id, provider_id, name, slot_minutes, created_at, auto_confirm)
SELECT gen_random_uuid(), '00000000-0000-0000-0000-00000000b001', '00000000-0000-0000-0000-00000000a001',
	'Sala ' || i, 60, now(), true
FROM generate_series(1, 20) AS i;

INSERT INTO resources (id, organization_id, provider_id, name, slot_minutes, created_at, auto_confirm)
VALUES ('00000000-0000-0000-0000-00000000c001', '00000000-0000-0000-0000-00000000b001',
	'00000000-0000-0000-0000-00000000a001', 'Sala za sudar', 60, now(), true);

-- Radnim danima od 8 do 20, satni termini
INSERT INTO availability_rules (id, resource_id, day_of_week, start_time, end_time)
SELECT gen_random_uuid(), r.id, d, '08:00', '20:00'
FROM resources AS r, generate_series(1, 5) AS d;
