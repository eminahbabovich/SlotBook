# Performanse

Ovdje su mjerenja s brojkama i s mašinom na kojoj su urađena. Load test (k6) dolazi u Fazi 9.

## Upiti statistike i indeks `bookings (resource_id, start_time)`

Mjereno 15.11.2026. na laptopu: Intel Core i5-1245U, 32 GB RAM, Windows 11, Docker Desktop s Docker Engine 29.9.2 (12 jezgri i 15,4 GB dato Dockeru). Baza je PostgreSQL 17.11 u privremenom kontejneru `postgres:17`, istom imageu kao u Testcontainersu, s podrazumijevanim podešavanjima. Šema je iz naših Flyway migracija, a podaci iz [`mjerenja/statistika-podaci.sql`](mjerenja/statistika-podaci.sql): 20 čitaonica sa po 10 stolova, satni termini kroz cijelu 2035., ukupno 408 270 rezervacija (tabela s indeksima 101 MB). Upiti su u [`mjerenja/statistika-explain.sql`](mjerenja/statistika-explain.sql). Za JPQL upite sam uzeo SQL koji Hibernate stvarno pošalje (`spring.jpa.show-sql`), a ne svoj prepis.

Prije mjerenja sam pretpostavio da statistika čita cijelu tabelu. Jedini indeks nad `bookings` koji počinje s `resource_id` je onaj iza `EXCLUDE`, a on je parcijalan, samo za `PENDING` i `CONFIRMED`. Statistika broji sve statuse, pa ga ne može koristiti. `EXPLAIN` je to potvrdio: svaki upit je bio `Parallel Seq Scan on bookings` preko svih 7 010 stranica, i za jedan mjesec jedne čitaonice. Problem nije bio sam broj od 20 ms, nego to što trošak raste s rezervacijama svih organizacija, a ne s podacima one koja pita.

Vrijeme iz `EXPLAIN (ANALYZE)`, za jednu čitaonicu (10 od 200 stolova), tri pokretanja zaredom, u milisekundama:

| Upit | Period | Bez indeksa | S indeksom |
|---|---|---|---|
| `countByStatus` | novembar | 22,0 / 18,6 / 17,9 | 2,6 / 2,7 / 2,2 |
| `countByStatusForProvider` | novembar | 21,6 / 16,4 / 16,4 | 1,2 / 1,1 / 1,2 |
| `countByDayAndHour` | novembar | 21,2 / 17,4 / 16,4 | 3,8 / 2,0 / 2,4 |
| `countByStatus` | cijela 2035. | 39,3 / 36,5 / 36,0 | 40,5 / 37,2 / 36,4 |
| `bookedMinutes` | cijela 2035. | 46,7 / 44,5 / 43,7 | 46,7 / 42,1 / 41,5 |

Za mjesec je razlika oko deset puta. Plan je sada `Bitmap Index Scan on bookings_resource_start` po jedan za svaki sto, a čita se oko 1 700 stranica umjesto 7 010. To je skoro po jedna stranica za svaku od ~1 660 rezervacija, jer su rezervacije jedne čitaonice rasute po tabeli, kao što bi bile i u stvarnom radu.

Za cijelu godinu indeks ne pomaže i planer ga ne bira. Provjerio sam da li je u pravu: sa `SET enable_seqscan = off` isti upit ide preko indeksa, dotakne 20 552 stranice za 20 380 rezervacija i traje 29 i 38 ms (dva pokretanja). To nije brže od čitanja cijele tabele od 7 010 stranica. Kad upit treba petinu stranica tabele, sekvencijalno čitanje je jeftinije.

Indeks je dodan migracijom `V27`. Na ovih 408 hiljada redova pravio se 276 ms i zauzima 16 MB. Svaki upis rezervacije sada ažurira jedan indeks više, a to nisam mjerio.

Šta nije mjereno: sve je bilo u memoriji (`shared hit`, osim 25 stranica u prvom pokretanju), pa brojke ne govore kako bi bilo na Neon-u, gdje baza nije na istoj mašini. Ne mjeri se ni ostatak zahtjeva. Ponuđene minute se računaju u Javi, s 2-3 upita po resursu (resurs, pravila, izuzeci), što je za čitaonicu s 10 stolova oko 30 malih upita. To ide u load test u Fazi 9. Ako godišnji izvještaj postane spor, sljedeći korak bi bio indeks koji pokriva i `status` i `end_time` (`INCLUDE`), da Postgres odgovori iz samog indeksa. To nisam probao.
