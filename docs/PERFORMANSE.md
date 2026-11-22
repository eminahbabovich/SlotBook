# Performanse

Ovdje su mjerenja s brojkama i s mašinom na kojoj su urađena.

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

## Broj upita po zahtjevu (N+1)

`UpitiPoZahtjevuApiTest` mjeri koliko SQL upita pošalje Hibernate za devet lista: resursi i članovi organizacije, moje rezervacije, raspored providera, slobodni termini, moja lista čekanja, iCal pretplata, moje organizacije i dnevnik. Svaka lista se mjeri s jednim redom, pa još jednom sa šest, i broj mora ostati isti. Brojač je `StatementInspector` koji broji samo upite iz niti testa. Hibernate statistika je zajednička za cijelu aplikaciju, pa bi u nju upao i `BookingJob`, koji radi svake minute.

Izmjereno 22.11.2026, isto za 1 i za 6 redova:

| Lista | Upita |
|---|---|
| resursi organizacije | 3 |
| članovi organizacije | 2 |
| moje rezervacije | 1 |
| raspored providera | 5 |
| slobodni termini (jedan dan) | 8, od Faze 10 10 |
| moja lista čekanja (od Faze 10) | 1 |
| iCal pretplata `.ics` (od Faze 10) | 2 |
| moje organizacije | 1 |
| dnevnik organizacije | 2 |

N+1 nije nađen nigdje. Liste koje čitaju relaciju već su imale `join fetch` (Faze 2-8). Test sam provjerio mutacijom: bez `join fetch b.client` u rasporedu broj ide sa 7 na 12 upita, po jedan za klijenta svake rezervacije, i test padne. `count` za stranicu se ne vidi u tabeli, jer ga Spring Data preskoči kad je prva stranica manja od veličine stranice.

Brojevi su stalni, ali nisu najmanji mogući. Slobodni termini su imali 8 upita, jer se resurs i organizacija čitaju jednom u provjeri pristupa (`@PreAuthorize`), a opet u servisu, u drugoj transakciji. To nisam dirao, jer ne raste s podacima. Lista čekanja (Faza 10, korak 2) je dodala još dva, ponude koje traju i moje upise, po jedan za cijeli period, a ne po terminu, pa ih je sad 10.

## EXPLAIN za upite rezervacija

Isti podaci i ista mašina kao gore (408 270 rezervacija, `postgres:17` u privremenom kontejneru, šema iz Flyway migracija), mjereno 22.11.2026. SQL je onaj koji Hibernate stvarno pošalje, uhvaćen u `UpitiPoZahtjevuApiTest`. Upiti su u [`mjerenja/liste-explain.sql`](mjerenja/liste-explain.sql). Sto je prvi iz `citaonica-1`, sedmica 19.-26.11.2035, a klijent je onaj s najviše rezervacija (255). Vrijeme iz `EXPLAIN (ANALYZE)`, tri pokretanja zaredom, u milisekundama:

| Upit | Plan | Stranica | Vrijeme |
|---|---|---|---|
| zauzeti termini (`FreeSlotService`) | `bookings_bez_preklapanja` (gist), 312 redova za 6 | 322 | 1,10 / 0,99 / 0,86 |
| raspored, prije `V33` | `bookings_resource_start`, 1 861 red za 33 | 1 906 | 2,84 / 2,79 / 2,77 |
| raspored, od `V33` | `bookings_resource_start`, 33 reda za 33 | 136 | 0,23 / 0,17 / 0,15 |
| moje rezervacije, prva stranica | `Index Scan Backward using bookings_client_start` | 88 | 0,17 / 0,15 / 0,20 |
| broj za stranicu | `bookings_idempotency_key` | 251 | 1,10 / 0,35 / 0,37 |

Raspored je jedini koji je rastao s historijom. Uslov je bio `start_time < kraj perioda AND end_time > početak`, a indeks `(resource_id, start_time)` može suziti samo prvi dio. Postgres je zato čitao sve rezervacije stola od prve do kraja traženog dana. Na ovim podacima je to 1 861 red za sedmicu u novembru, a poslije pet godina rada bilo bi pet puta više. Rezervacija ne može trajati duže od najdužeg termina, 480 minuta, pa ona koja dira period počinje najviše 8 sati prije njega. Upit sad ima i `start_time > početak - 8 sati`, i indeks čita tačno onih 33 reda. Da granica ne zavisi samo od Jave, `V33` je dodala `CHECK (end_time - start_time <= INTERVAL '8 hours')`. Na 408 hiljada redova provjera postojećih redova je trajala 48 ms.

Zauzeti termini koriste gist indeks iza `EXCLUDE`, ali samo po `resource_id`. Raspon iz indeksa ne pomaže, jer upit poredi kolone, a ne `tstzrange`. Čita sve aktivne rezervacije stola (312), a ne historiju, jer završene više nisu `PENDING` ni `CONFIRMED`. To raste s brojem budućih rezervacija, a ne sa starim, pa sam ga ostavio.

Šta nije mjereno: sve je opet bilo u memoriji, a brojke za Neon nemam. Ni ovdje se ne mjeri cijeli zahtjev, to radi load test ispod.

## Load test (k6)

Mjereno 22.11.2026. na istom laptopu: Intel Core i5-1245U, 32 GB RAM, Windows 11. Backend je jar (`mvnw package`) na Javi 21.0.12, direktno na Windowsu, s podrazumijevanim podešavanjima: Hikari pul od 10 konekcija i Tomcat s 200 niti. Baza je PostgreSQL 17.11 u privremenom kontejneru `postgres:17`, a k6 je `grafana/k6:2.3.0`, isto u Dockeru (Docker Engine 29.9.2, 12 jezgri i 15,4 GB). Sve je na jednoj mašini, pa k6, backend i baza dijele procesor, a mreže između njih praktično nema.

Podaci su iz [`mjerenja/k6-podaci.sql`](mjerenja/k6-podaci.sql): teretana s 20 sala, jedna sala za sudar i 100 članova. Skripta je [`mjerenja/k6-rezervacije.js`](mjerenja/k6-rezervacije.js). Prije testa se svih 100 članova prijavi (`setup`). Onda 60 sekundi istovremeno traju tri scenarija:

- slobodni termini: 50 zahtjeva u sekundi, sedmični kalendar nasumične sale;
- rezervacije: 10 u sekundi, svaka na svoj termin (sala i sat iz broja iteracije), pa sudara ne bi smjelo biti;
- sudar: 20 VU-ova, svaki po 30 iteracija, i svi u n-toj iteraciji traže isti termin u sali za sudar.

Tri pokretanja. Između njih su rezervacije obrisane (`truncate`), a backend je radio cijelo vrijeme, pa je prvo pokretanje na hladnom JVM-u. Vremena su u milisekundama, medijana / p95:

| Scenarij | 1. | 2. | 3. |
|---|---|---|---|
| slobodni termini | 17,7 / 95,6 | 15,5 / 68,7 | 13,2 / 71,7 |
| rezervacije | 26,9 / 104,2 | 21,9 / 72,9 | 19,5 / 76,2 |
| sudar | nije mjereno posebno | 125,8 / 165,7 | 130,3 / 184,4 |

U svakom pokretanju bilo je 4 303 zahtjeva (oko 69 u sekundi), 0 neuspjelih, 601 rezervacija s `201`, i u sudaru 30 puta `201` i 570 puta `409`. Dakle, po terminu je prošao tačno jedan od 20. Poslije svakog pokretanja baza je imala 30 rezervacija u sali za sudar, 601 u ostalim i 631 zapis u `booking_events`, isto kao brojači u k6. U prvom pokretanju scenarij sudara nije imao svoj prag, pa ni posebno vrijeme. Prag sam dodao poslije.

Sudar je oko šest puta sporiji od obične rezervacije. To je očekivano: rezervacije istog resursa idu jedna za drugom kroz `FOR UPDATE` na resursu (vidi `BookingService.create`), pa 20 zahtjeva za istu salu čeka u redu. Zahtjev za drugu salu ne čeka.

Usput sam vidio da svaki `409` ostavi u logu dvije WARN linije Hibernatea (`SQLState: 23P01` i poruka Postgresa). U jednom pokretanju to je 1 140 linija. Na Renderu bi to bila buka u logu, ali nisam ništa mijenjao.

Šta ovo ne pokazuje: koliko izdrži Render. Besplatni plan ima 0,1 CPU i 512 MB, a Neon je preko mreže, pa bi brojke tamo bile drugačije. Test na produkciji namjerno nisam radio. Ne pokazuje ni gdje je granica. Opterećenje je bilo fiksno (60 zahtjeva u sekundi plus sudar), a ne povećavano dok nešto ne pukne.
