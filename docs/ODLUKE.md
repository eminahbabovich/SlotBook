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

API je stateless: server ne pamti sesiju, svaki zahtjev nosi token u `Authorization` zaglavlju. CSRF napad radi tako što browser sam pošalje kolačić uz zahtjev sa tuđe stranice, a zaglavlje ne šalje sam, pa je CSRF zaštita isključena. Jedini kolačić je refresh token, a zašto ni za njega ne treba CSRF token, piše kod refresh tokena ispod.

Bez prijave se dobija `401` i za adresu koja ne postoji, a ne `404`. Tako neprijavljen posjetilac ne može isprobavanjem saznati koje adrese API ima.

## springdoc 3.x, ne 2.x

Većina tutorijala navodi springdoc 2.8, ali to je linija za Spring Boot 3. Za Boot 4 je v3. Koristim 3.2.0, koja je izgrađena na Spring Bootu 4.1.2, što je moja verzija (vidi se u njenom POM-u).

## Skeniranje tajni: gitleaks iz Docker slike

Repo je privatan, a GitHub secret scanning se za privatne repoe na besplatnom nalogu ne nudi (na stranici postavki te sekcije jednostavno nema). Zato u CI-ju pokrećem gitleaks nad cijelom git historijom, direktno iz zvanične Docker slike zakucane na verziju, a ne preko tuđe akcije. Ograničenje: Dependabot neće sam javiti novu verziju gitleaksa, pa je podižem ručno.

Gitleaks je prijavio lažni `JWT_SECRET` u `.env.example` (pravilo `generic-api-key`, commit `b1e9f1d`), i CI je od tog commita bio crven, a da to nisam primijetio. Promjena vrijednosti ne bi pomogla, jer stari commit ostaje u historiji. Zato je u `.gitleaksignore` samo taj jedan nalaz (commit, fajl, pravilo, linija), a ne cijeli `.env.example`: prava tajna upisana u taj fajl i dalje obara CI.

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

Token koji je jednom izdat ne može se poništiti dok ne istekne. Zato traje samo 15 minuta, a duže sesije drži refresh token, koji se može opozvati.

Prijava za nepoznat email i za pogrešnu lozinku vraća isti odgovor. Za nepoznat email se svejedno pokrene BCrypt nad lažnim hashom, jer bi inače odgovor bio oko 100 ms brži, pa bi se mjerenjem moglo saznati ko ima nalog.

## Refresh token: httpOnly kolačić, rotacija i detekcija krađe

Refresh token je 32 nasumična bajta i traje 30 dana. Svaki `/api/auth/refresh` ga zamijeni novim, pa novi opet važi 30 dana. Ko je aktivan, ne odjavljuje se.

Klijent ga dobija u kolačiću s `HttpOnly`, `Secure`, `SameSite=Strict` i `Path=/api/auth`. Druga opcija je bila tijelo odgovora, ali tada ga frontend mora čuvati u `localStorage`, odakle ga XSS napad može pročitati, ili samo u memoriji, pa se odjavljuje na svaki refresh stranice. Cijena kolačića je da frontend i backend moraju biti na istom sajtu: Safari ne šalje kolačić s `vercel.app` na `onrender.com`. Zato live demo treba vlastiti domen s poddomenama (Faza 12). Lokalno to rješava Vite proxy.

CSRF token ne koristim ni za `/refresh`. `SameSite=Strict` znači da browser kolačić ne šalje uz zahtjev pokrenut s tuđeg sajta. I da ga pošalje, lažni zahtjev na `/refresh` napadaču ništa ne daje: novi access token dolazi u odgovoru, a odgovor sa tuđeg sajta ne može pročitati zbog CORS-a. Na `/logout` može nekoga odjaviti, što je smetnja, ali ne krađa.

U bazi je samo SHA-256 tokena. Ne BCrypt, jer token od 32 nasumična bajta se ne može pogoditi pa mu sporost ne treba, a hash mora biti uvijek isti da bih token našao u bazi.

Tokeni iz jedne prijave čine porodicu (`family_id`). Kad stigne token koji je već zamijenjen, neko ga je kopirao, i ne znam da li ga šalje lopov ili vlasnik. Zato se poništi cijela porodica i oba se moraju ponovo prijaviti. Druge prijave istog korisnika (drugi uređaj) ostaju.

Dvije stvari sam naučio na testovima. Prva: porodicu poništim pa bacim izuzetak, a izuzetak u `@Transactional` metodi vrati sve unazad, pa i poništavanje. Bez `noRollbackFor` test pokaže da ukradena porodica i dalje radi. Druga: bez `SELECT ... FOR UPDATE` osam istovremenih zahtjeva s istim tokenom svih osam uspije, i iz jednog tokena nastane osam važećih. Sa zaključavanjem uspije tačno jedan.

Posljedica koju moram riješiti na frontendu: ako dva taba ili dva zahtjeva u istom trenutku pošalju isti refresh token, drugi izgleda kao krađa i korisnik se odjavi. Frontend zato mora raditi samo jedan refresh u isto vrijeme (Faza 7). Neki sistemi dozvole par sekundi u kojima stari token još važi. To nisam uradio, jer otvara prozor baš za ono što detekcija treba zaustaviti.

Stari redovi se još ne brišu. Posao koji čisti istekle tokene dolazi s ostalim `@Scheduled` poslovima u Fazi 5.

## Organizacije i članstvo

Članstvo (`organization_members`) nema svoj ID. Ključ je par (`organization_id`, `user_id`), pa baza sama ne pušta istog korisnika dvaput u istu organizaciju, a isti korisnik i dalje može biti `PROVIDER` u teretani i `CLIENT` u čitaonici. Oba slučaja imaju test. U Javi je to `@EmbeddedId` s recordom, jer Hibernate ključeve poredi preko `equals` i `hashCode`, a record ih daje sam.

Uloga je `VARCHAR` sa `CHECK (role IN ('ADMIN', 'PROVIDER', 'CLIENT'))`, a u entitetu `EnumType.STRING`. Sa `ORDINAL` bi u bazi pisalo 0, 1, 2, i nova uloga ubačena ispred postojećih bi tiho promijenila značenje starih redova.

Ko napravi organizaciju, postaje njen `ADMIN` u istoj transakciji. Organizacija bez admina ne bi imala ko da je uređuje. Zauzet slug radi isto kao zauzet email: odmah se upisuje, a `UNIQUE` odlučuje i vraća se `409`. Format sluga (mala slova bez kvačica, brojevi, crtice između riječi) provjerava i `@Pattern` u zahtjevu i `CHECK` u bazi. Slug se ne može mijenjati (`updatable = false`), jer je dio javne adrese.

`GET /api/organizations/{slug}` je otvoren bez prijave, jer klijent treba vidjeti gdje rezerviše prije nego što napravi nalog. Vraća samo ID, naziv, slug i zonu, bez članova.

Zona mora biti IANA naziv (`Europe/Sarajevo`). `ZoneId.of` bi prihvatio i `+01:00`, a fiksni pomak ne zna za ljetno vrijeme, pa bi termini od kraja marta do kraja oktobra bili sat pomjereni. Zato `@RegionTimeZone` provjerava listu `ZoneId.getAvailableZoneIds()`. Provjerio sam da ta lista ne sadrži `+01:00`, ali sadrži `Etc/GMT-1`, koji je i u julu `+01:00`. Takvu zonu validator za sada pušta. Ko je upiše, upisao ju je namjerno, ali to nije ista zaštita kao za `+01:00`.

## Autorizacija po organizaciji: @PreAuthorize i bean koji pita bazu

Filter chain zna samo da li je token ispravan. Da li neko smije vidjeti članove baš ove organizacije ovisi o ID-ju iz putanje i o redu u `organization_members`, pa to odlučuje `@PreAuthorize` na metodi kontrolera:

`@PreAuthorize("@organizationAccess.hasRole(#id, authentication, 'ADMIN')")`

`OrganizationAccess` po primarnom ključu članstva (organizacija, korisnik) provjeri ima li korisnik jednu od traženih uloga. Druga opcija je bio `PermissionEvaluator` (`hasPermission(#id, 'Organization', 'ADMIN')`), jedan evaluator za sve tipove. Bean mi je jasniji: ime metode kaže šta se provjerava, a nema `if` po stringu s nazivom tipa.

Anotacija stoji na kontroleru, uz rutu, a ne na servisu. Tako se kod svakog endpointa vidi ko ga smije zvati, a servis se testira bez security konteksta. Cijena je da servisna metoda pozvana iz drugog mjesta nije zaštićena sama po sebi.

Bez `@EnableMethodSecurity` Spring `@PreAuthorize` tiho ignoriše. Probao sam: aplikacija se normalno pokrene, a admin druge organizacije dobije `200` i tuđe emailove. Zato svaki zaštićeni endpoint ima test kroz HTTP s korisnikom iz tuđe organizacije, a ne samo test beana.

Ko nije član, dobije `403`, isto kao član bez uloge i kao neko ko pita za ID koji ne postoji. `404` za ne-člana bi sakrio da organizacija postoji, ali ID je UUID i ne može se pogoditi, a javni profil je ionako otvoren. `403` je ono što Spring vrati sam kad provjera odbije.

Lista članova učitava korisnike istim upitom (`join fetch`). Izmjerio sam na testu s dva člana: bez toga idu još dva `SELECT`-a na `users`, po jedan za svakog člana. Test ovo ne hvata, jer je rezultat isti, samo sporiji. To je posao za Fazu 9 i traženje N+1 upita. Paginacije na ovoj listi još nema, dolazi s ostalim listama u Fazi 3.

## Pozivnice u organizaciju

Admin pozove email s ulogom, a osoba postaje član kad pozivnicu prihvati prijavljena, s istim emailom. Token je isti kao kod refresh tokena (32 nasumična bajta, u bazi samo SHA-256), pa sam generisanje i hash izvukao u `RandomToken`. Pozivnica važi 7 dana.

Dok ne dođe slanje emaila (Faza 5), `POST /api/organizations/{id}/invites` vraća token adminu i on ga sam proslijedi. To je u redu jer token sam ništa ne otvara: prihvatiti ga može samo neko ko je prijavljen s emailom iz pozivnice. U Fazi 5 token prestaje da se vraća.

Prihvatanje je `POST /api/invites/accept` s tokenom u tijelu. U skici je token bio u putanji, ali putanja ostaje u logovima servera i Render-a, a tijelo ne.

Nova pozivnica za isti email zamjenjuje staru neprihvaćenu: novi token, nova uloga, novi rok, a stari link više ne radi. Bazu tu čuva parcijalni UNIQUE indeks, jedna otvorena pozivnica po emailu u organizaciji (`WHERE accepted_at IS NULL`), pa prihvaćene ostaju kao historija i ne smetaju. Staru briše JPQL `delete` s `@Modifying`, koji ide u bazu odmah. Prvo sam mislio da je svejedno, ali Hibernate pri flushu radi `INSERT` prije `DELETE`-a. Sa Spring Data `deleteBy...` drugi poziv padne s `duplicate key value violates unique constraint "organization_invites_jedna_otvorena"` i vrati `500`, to sam vidio u logu.

Kad prihvatanje ne prođe, status kaže šta da korisnik uradi: `404` nepoznat token, `403` pozivnica je za drugi email, `409` već iskorištena, `410` istekla (traži novu). To je jedan izuzetak s enumom razloga i jedan `switch` u handleru.

Pozivnica se označava iskorištenom uslovnim `UPDATE ... SET accepted_at = ... WHERE id = ? AND accepted_at IS NULL`, a ne provjerom u Javi. Test s 8 niti i istim tokenom daje tačno jedan uspjeh i sedam `409`. Mislio sam da bi bez uslova ostali dobili `500`, ali nisu: `UPDATE` i bez uslova zaključa red, pa ostali sačekaju i dobiju "već je član". Uslov zato ne čuva od duplog članstva (to čuva PK), nego od ponovne upotrebe: iskorištena pozivnica ostaje iskorištena i kad člana jednom budemo mogli ukloniti.

Šta nije riješeno:

- Verifikacija emaila je tek u Fazi 5. Do tada neko može registrovati tuđi email i, ako dođe do tokena, prihvatiti pozivnicu poslanu na taj email.
- Ako dva admina u istom trenutku pozovu isti email, oba obrišu staru (koje nema) i oba upisuju. Jedan dobije `500` od indeksa. Pravilo u bazi ostaje tačno, ali odgovor nije lijep. Nisam to testirao.
- Pozivnica se ne može opozvati, a stari redovi se ne brišu. Opoziv nije u planu Faze 2, a čišćenje dolazi s `@Scheduled` poslovima u Fazi 5.

## Test izolacije organizacija i čuvar ruta

Kriterij Faze 2 kaže da korisnik iz organizacije A ne može dotaknuti podatke organizacije B. `OrganizationIsolationApiTest` to provjerava s adminom druge organizacije, a ne s običnim članom, jer samo on otkriva grešku tipa "ima ulogu ADMIN bilo gdje". Na svakom endpointu dobije `403`, a broj članova i pozivnica u tuđoj organizaciji ostane isti. Isti zahtjevi s tokenom pravog admina moraju proći. Bez toga bi zahtjev s greškom u putanji ili tijelu pao iz pogrešnog razloga i test bi bio zelen. Tijela moraju biti ispravna jer se `@Valid` izvrši prije `@PreAuthorize`, pa bi neispravno tijelo dalo `400` i sakrilo da provjera fali.

Ručna lista endpointa zastari čim se doda novi. Zato test preko `RequestMappingHandlerMapping` pročita sve rute iz aplikacije i padne ako postoji ruta pod `/api/organizations/{...}/` koje nema u listi. Probao sam s privremenim `GET /api/organizations/{id}/stats`: test pada i u poruci imenuje tu rutu. Kad sam pozivnicama zamijenio provjeru s `isAuthenticated()`, prvi test je pao s `expected: 403 but was: 201`.

Čuvar vidi samo putanje gdje je ID organizacije u adresi. Resursi (`/api/resources/{id}`) i rezervacije (`/api/bookings/{id}`) iz faza 3 i 4 pripadaju organizaciji, ali ID organizacije nije u putanji, pa ih čuvar za sada ne bi primijetio. Filter se proširuje kad ti endpointi nastanu, a test kroz sve endpointe je u Fazi 8.
