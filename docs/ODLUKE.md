# Odluke

Ovdje zapisujem odluke koje sam donio u toku rada i zašto. Dodajem ih kad ih donesem, a ne na kraju.

## Zaštita od preklapanja termina: EXCLUDE constraint

Preklapanje rezervacija istog resursa sprečava baza, preko `EXCLUDE USING gist` constrainta, a ne samo Java kod. Java kod može provjeriti "ima li preklapanja" prije upisa, ali dva zahtjeva koja stignu istovremeno oba vide da je slobodno. Baza to rješava za svaku konekciju, i za upit koji neko ručno pokrene.

Potrebna je ekstenzija `btree_gist`, jer constraint kombinuje jednakost (`resource_id =`) i preklapanje raspona (`termin &&`). Prva migracija je uključuje.

Provjerio sam na Neon-u (PostgreSQL 17.11) prije nego što sam išta gradio na tome. `create extension btree_gist` prolazi, termini 9-10 i 10-11 mogu postojati zajedno (`tstzrange` ima kraj isključen), a preklapanje 9:30-10:30 vraća `conflicting key value violates exclusion constraint` sa SQLSTATE `23P01`. Isto radi s `resource_id` tipa `uuid` i `bigint`. Plan B (`SELECT ... FOR UPDATE`) mi zato ne treba. Poređenje dva pristupa pišem u Fazi 4, kad ga implementiram.

## PostgreSQL 17 svugdje

Lokalno (Docker Compose), u testovima (Testcontainers) i na Neon-u koristim istu glavnu verziju, 17. Prvo sam u testovima imao `postgres:latest`, što je bilo 18.6, i primijetio da bi se verzija mogla promijeniti bez ikakve izmjene u kodu. Neon podržava 14 do 18, pa sam uzeo 17 kao stariju i provjerenu.

## Vrijeme dolazi iz Clock-a, ne iz Instant.now()

Servisi i kontroleri dobiju `Clock` kroz konstruktor. U produkciji je to `Clock.systemUTC()`, a u testu `Clock.fixed(...)`. Pravila poput "otkazivanje najkasnije 2h prije termina" ili istek nepotvrđene rezervacije ne mogu se pouzdano testirati ako se vrijeme ne može zamrznuti.

## Greške u formatu Problem Details

Svaka greška API-ja vraća `application/problem+json` (RFC 9457), pa frontend ima jedan način čitanja grešaka. Poruke su na bosanskom, standardne (404, 405, pokvaren JSON) se prevode kroz `messages.properties`, a validacija vraća popis polja. Neočekivani izuzeci vraćaju opću poruku, a stvarni uzrok ide samo u log, jer poruka izuzetka može sadržavati SQL ili putanje.

Spring Security baca `AuthenticationException` i `AccessDeniedException`. Bez posebnih handlera bi ih `Exception.class` handler vratio kao `500`, pa za njih postoje odvojeni handleri koji vraćaju `401` i `403`. Greške koje nastanu u filterima, prije kontrolera (zahtjev bez prijave), `SecurityConfig` prosljeđuje u isti `GlobalExceptionHandler` preko `HandlerExceptionResolver`-a, da format bude jedan.

## Bez sesija i bez CSRF zaštite

API je stateless: server ne pamti sesiju, svaki zahtjev nosi token u `Authorization` zaglavlju. CSRF napad radi tako što browser sam pošalje kolačić uz zahtjev sa tuđe stranice, a zaglavlje ne šalje sam, pa je CSRF zaštita isključena. Ovo vrijedi dok nijedan token nije u kolačiću. Ako refresh token završi u httpOnly kolačiću, `/api/auth/refresh` opet treba zaštitu.

Bez prijave se dobija `401` i za adresu koja ne postoji, a ne `404`. Tako neprijavljen posjetilac ne može isprobavanjem saznati koje adrese API ima.

## springdoc 3.x, ne 2.x

Većina tutorijala navodi springdoc 2.8, ali to je linija za Spring Boot 3. Za Boot 4 je v3. Koristim 3.2.0, koja je izgrađena na Spring Bootu 4.1.2, što je moja verzija (vidi se u njenom POM-u).

## Skeniranje tajni: gitleaks iz Docker slike

Repo je privatan, a GitHub secret scanning se za privatne repoe na besplatnom nalogu ne nudi (na stranici postavki te sekcije jednostavno nema). Zato u CI-ju pokrećem gitleaks nad cijelom git historijom, direktno iz zvanične Docker slike zakucane na verziju, a ne preko tuđe akcije. Ograničenje: Dependabot neće sam javiti novu verziju gitleaksa, pa je podižem ručno.

## Neon: direktna adresa baze, ne pooler

Neon daje dvije adrese za istu bazu. Ona s `-pooler` u hostu ide kroz PgBouncer. Neon u dokumentaciji piše da taj pul ne podržava sesijske advisory lockove, a Flyway pri startu zaključava šemu advisory lockom. Zato aplikacija koristi direktnu adresu. Nisam probao pooler da vidim šta se desi, samo sam se oslonio na dokumentaciju. Pul konekcija mi ionako ne treba od Neon-a, jer ga drži Hikari unutar aplikacije.

Na Renderu se baza podešava preko tri varijable (`DB_URL`, `DB_USER`, `DB_PASSWORD`), a ne jednog stringa. Neon-ov string ima korisnika i lozinku unutar adrese, a JDBC URL ih ne treba, pa u `DB_URL` ide samo `jdbc:postgresql://HOST/neondb?sslmode=require`.

## ID-jevi su UUID, ne bigint

ID organizacije, resursa i rezervacije stoji u URL-u. Sa `bigint` se iz `/api/bookings/1043` vidi otprilike koliko rezervacija postoji, a susjedni broj se lako proba. Autorizacija ionako mora odbiti tuđi ID, ali UUID se ne može pogoditi, pa greška u autorizaciji nije odmah iskoristiva. Cijena je 16 bajtova umjesto 8 i nešto veći indeksi, što na ovoj veličini baze ništa ne mijenja. UUID generiše Hibernate (`GenerationType.UUID`) prije upisa, pa entitet ima ID i bez povratka u bazu.

## Email je uvijek malim slovima, i to čuva baza

`Amra@example.com` i `amra@example.com` su isti nalog. `User` spušta email u mala slova (s `Locale.ROOT`, jer na turskom locale-u `I` postaje `ı`, i to imam test koji pada bez toga), a tabela ima `CHECK (email = lower(email))`. Bez CHECK-a bi `UNIQUE` pustio oba reda čim ih neko upiše mimo Jave. Razmatrao sam `citext` ekstenziju, ali za jednu kolonu mi je CHECK jednostavniji i ne treba još jedna ekstenzija na Neon-u.

## Zauzet email: odlučuje UNIQUE, ne provjera unaprijed

Registracija ne pita prvo "postoji li ovaj email". Dva zahtjeva u istom trenutku bi oba dobila "ne postoji" i oba bi pokušala upis. Zato servis odmah upisuje, a kad `UNIQUE` odbije red, `DataIntegrityViolationException` prevodi u `409`. Isti problem me čeka u Fazi 4 s terminima, samo veći.

`409` s porukom "nalog već postoji" otkriva da je neko registrovan s tim emailom (enumeracija). Za sada je tako, jer je jasnije za korisnika. U Fazi 8 to rješavam zajedno s verifikacijom emaila: registracija tada uvijek vraća isti odgovor, a vlasnik emaila dobije poruku.

## Lozinka najviše 72 bajta

BCrypt koristi samo prva 72 bajta lozinke, a Spring Security 7 za dužu lozinku baca `IllegalArgumentException`, što bi bilo `500`. `@Size` broji znakove, a `č` je dva bajta u UTF-8, pa sam napravio `@MaxUtf8Bytes(72)`. Test s 37 slova `č` (74 bajta) bez te anotacije stvarno vrati `500`, provjerio sam.

## Access token: JWT, HS256, 15 minuta

Prijava vraća JWT koji Spring Securityjev resource server provjerava na svakom zahtjevu (potpis i rok), pa ne pišem vlastiti filter. Potpis je HS256, isti tajni ključ (`JWT_SECRET`) potpisuje i provjerava. RS256 (privatni ključ potpisuje, javni provjerava) ima smisla kad tokene provjerava neki drugi servis, a ovdje je sve u jednoj aplikaciji.

U tokenu je samo ID korisnika (`sub`), rok i izdavač. Uloge nisu, jer su po organizaciji i mogu se promijeniti dok token još važi. Ako admin nekome oduzme ulogu, to treba važiti odmah, a ne za 15 minuta, pa se uloge čitaju iz baze kod svakog zahtjeva (korak 7).

Token koji je jednom izdat ne može se poništiti dok ne istekne. Zato traje samo 15 minuta, a duže sesije će držati refresh token, koji se može opozvati.

Prijava za nepoznat email i za pogrešnu lozinku vraća isti odgovor. Za nepoznat email se svejedno pokrene BCrypt nad lažnim hashom, jer bi inače odgovor bio oko 100 ms brži, pa bi se mjerenjem moglo saznati ko ima nalog.
