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

Zona mora biti IANA naziv (`Europe/Sarajevo`). `ZoneId.of` bi prihvatio i `+01:00`, a fiksni pomak ne zna za ljetno vrijeme, pa bi termini od kraja marta do kraja oktobra bili sat pomjereni. Zato `@RegionTimeZone` provjerava listu `ZoneId.getAvailableZoneIds()`. Ta lista ne sadrži `+01:00`, ali sadrži `Etc/GMT-1`, koji je i u julu `+01:00`, pa je prva verzija validatora tu zonu puštala. Sada validator odbija i svaku zonu čija pravila nikad nemaju prelaz (`ZoneRules.isFixedOffset()`). Na Javi 21.0.12 to je 49 od 604 zone: sve `Etc/*`, `UTC`, `GMT` i njihovi sinonimi i sedam `SystemV/*`. Prije toga sam probao `Asia/Tokyo` i `Asia/Kolkata`, koje danas nemaju ljetno vrijeme, i obje prolaze jer su ga nekad imale. Mjesto bez ljetnog vremena tako i dalje može izabrati svoju zonu. Cijena je da organizacija ne može izabrati `UTC`, ali nijedna čitaonica ni teretana ne radi po UTC-u, nego po satu mjesta gdje se nalazi.

Ovo čuva samo Java. `CHECK` u Postgresu ne smije imati podupit, pa ne može pitati `pg_timezone_names` je li zona poznata. Termine ionako računa Java, pa je bitno da zonu poznaje Java.

## Autorizacija po organizaciji: @PreAuthorize i bean koji pita bazu

Filter chain zna samo da li je token ispravan. Da li neko smije vidjeti članove baš ove organizacije ovisi o ID-ju iz putanje i o redu u `organization_members`, pa to odlučuje `@PreAuthorize` na metodi kontrolera:

`@PreAuthorize("@organizationAccess.hasRole(#id, authentication, 'ADMIN')")`

`OrganizationAccess` po primarnom ključu članstva (organizacija, korisnik) provjeri ima li korisnik jednu od traženih uloga. Druga opcija je bio `PermissionEvaluator` (`hasPermission(#id, 'Organization', 'ADMIN')`), jedan evaluator za sve tipove. Bean mi je jasniji: ime metode kaže šta se provjerava, a nema `if` po stringu s nazivom tipa.

Anotacija stoji na kontroleru, uz rutu, a ne na servisu. Tako se kod svakog endpointa vidi ko ga smije zvati, a servis se testira bez security konteksta. Cijena je da servisna metoda pozvana iz drugog mjesta nije zaštićena sama po sebi.

Bez `@EnableMethodSecurity` Spring `@PreAuthorize` tiho ignoriše. Probao sam: aplikacija se normalno pokrene, a admin druge organizacije dobije `200` i tuđe emailove. Zato svaki zaštićeni endpoint ima test kroz HTTP s korisnikom iz tuđe organizacije, a ne samo test beana.

Ko nije član, dobije `403`, isto kao član bez uloge i kao neko ko pita za ID koji ne postoji. `404` za ne-člana bi sakrio da organizacija postoji, ali ID je UUID i ne može se pogoditi, a javni profil je ionako otvoren. `403` je ono što Spring vrati sam kad provjera odbije.

Lista članova učitava korisnike istim upitom (`join fetch`). Izmjerio sam na testu s dva člana: bez toga idu još dva `SELECT`-a na `users`, po jedan za svakog člana. Test ovo ne hvata, jer je rezultat isti, samo sporiji. To je posao za Fazu 9 i traženje N+1 upita. Od Faze 3 lista ide po stranicama, vidi paginaciju ispod.

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

## Resursi: provider mora biti član iste organizacije, i to čuva baza

Resurs (sala, sto u čitaonici, termin konsultacija) pripada organizaciji i ima providera, osobu koja ga vodi. Prvo sam mislio na običan FK `provider_id REFERENCES users`, ali on pušta i korisnika iz druge organizacije. Zato je ključ složen: `(organization_id, provider_id)` mora postojati kao par u `organization_members`. Probao sam test s običnim FK-om i Lejla, trenerica iz teretane, postane provider stola u čitaonici čija nije članica. Sa složenim ključem baza vrati `resources_provider_clan`.

To se pokazalo i kad sam privremeno obrisao `@PreAuthorize` s kreiranja resursa. Bojan, admin druge organizacije, nije dobio `201`, nego `500`, jer je upis odbila baza. Odgovor nije dobar i test izolacije je pao kako treba, ali ništa nije upisano. Ključ čuva samo članstvo, ne ulogu: `CLIENT` bi prošao bazu, njega zaustavlja samo `@PreAuthorize`.

Provider novog resursa je uvijek onaj ko ga pravi, admin ili provider. Da zahtjev ima `providerId`, trebalo bi još jedno pravilo (provider ne smije dodijeliti resurs drugome, klijent ne smije biti provider). Promjena providera ide uz izmjenu resursa.

Iz skice modela u tabelu nisu ušle kolone koje ova faza ne koristi: `auto_confirm` i `min_notice_hours` dolaze s rezervacijama u Fazi 4, `capacity` u Fazi 10, cijena u Fazi 11, a `image_key` s uploadom slike. Svaka kolona dolazi s kodom i testom koji je koriste, po cijenu nekoliko `ALTER TABLE` migracija više.

Servis resursa dolazi do organizacije preko `OrganizationService.getReference`, a ne preko `OrganizationRepository`, koji je vidljiv samo u paketu `organization`. Drugi paketi tako ne mogu direktno pisati po organizacijama.

## Paginacija: vlastiti oblik stranice i redoslijed koji bira server

Liste resursa i članova primaju `?page=0&size=20`, a Spring od toga sam napravi `Pageable`. Repozitorij vrati `Page`, za koji Spring Data pošalje dva upita: jednu stranicu i `COUNT` za ukupan broj.

`Page` ne vraćam direktno. U Spring Bootu 4.1.2 `spring.data.web.pageable.serialization-mode` je podrazumijevano `direct` (pogledao sam metapodatke u `spring-boot-data-commons-4.1.2.jar`), a za taj oblik Spring Data ispisuje upozorenje "there is no guarantee about the stability of the resulting JSON structure" (tekst sam našao u klasi `WarningLoggingModifier` u `spring-data-commons-4.1.2.jar`). Isto upozorenje predlaže `VIA_DTO`, gdje Spring Data vraća svoj `PagedModel`. I to bi bilo stabilno. Uzeo sam vlastiti `PageResponse` sa pet polja (`content`, `page`, `size`, `totalElements`, `totalPages`), jer je oblik odgovora tada u mom kodu i ne zavisi od globalne postavke. Lista članova je do sada vraćala običan niz, pa se njen oblik promijenio. Frontenda još nema, pa to nikoga ne lomi.

Sort iz zahtjeva se ignoriše, a redoslijed određuje servis: resursi po nazivu, članovi po datumu ulaska. Probao sam šta se desi kad se sort iz zahtjeva propusti: `?sort=provider.passwordHash` vrati `200` s resursima, znači Spring Data pusti sortiranje i po koloni s hashom tuđe lozinke. Iz redoslijeda se onda može zaključivati o hashu. Sam SQL tada nisam ispisao. Drugo polje u sortu (`id`) je tu jer dva resursa s istim nazivom inače nemaju stalan redoslijed, pa bi isti resurs mogao završiti na dvije stranice ili ni na jednoj.

Stranica ima najviše 100 redova (`spring.data.web.pageable.max-page-size`). Spring Data podrazumijevano pušta 2000, a `?size=5000` sada vrati `size: 100`, ima test.

Za listu članova sam prvo ručno napisao `countQuery`, jer sam mislio da Hibernate odbija `count` s `join fetch`. Kad sam ga obrisao, testovi su prošli. Spring Data sam izvede `COUNT` iz upita, pa ručnog više nema.

## Izmjena resursa: provjera ide preko resursa, ne preko putanje

Admin mijenja svaki resurs organizacije, a provider samo one koje vodi. To odlučuje `@resourceAccess.canManage(#resourceId, authentication)`: učita resurs, iz njega uzme organizaciju i u bazi provjeri ulogu. Organizaciju iz putanje ovdje ne gledam, jer bi admin teretane onda stavio svoju teretanu u putanju i ID tuđeg resursa iza nje.

Servis zatim traži resurs po paru (resurs, organizacija iz putanje). Ako se ne poklapaju, vraća `404`, i kad je korisnik admin obje organizacije. Bez toga bi `PUT /api/organizations/teretana/resources/{sto iz čitaonice}` promijenio sto u čitaonici, i test to pokaže kad servis traži samo po ID-ju.

Dva testa sam provjerio privremenom izmjenom koda. Bez uslova "provider baš ovog resursa" trener mijenja i saunu koju vodi admin. Bez para (resurs, organizacija) prolazi izmjena kroz pogrešnu putanju.

## Deaktivacija umjesto brisanja

`DELETE .../resources/{resourceId}` ne briše red, nego postavlja `active = false`. Na resurs će u Fazi 4 pokazivati rezervacije, a obrisan red bi ili pao na stranom ključu ili odnio historiju termina. Drugi `DELETE` nad istim resursom isto vrati `204`, jer je stanje već ono koje je traženo.

Klijent u listi vidi samo aktivne resurse, a admin i provider i deaktivirane, s `active: false`. Ulogu servis čita iz baze (`OrganizationService.roleOf`), što je još jedan upit po listi, pored onog u `@PreAuthorize`.

Ovdje me baza ne čuva kao kod kreiranja. Kad sam privremeno obrisao `@PreAuthorize` s `DELETE`, Bojan iz druge organizacije je dobio `204` i stvarno deaktivirao sto u čitaonici, jer se ne upisuje novi red pa složeni ključ nema šta provjeriti. Ovu rutu čuva samo provjera u Javi, a test izolacije padne kad je nema.

Šta nije urađeno: deaktiviran resurs se još ne može ponovo aktivirati, a u Fazi 4 rezervacija na deaktiviran resurs mora biti odbijena.

## Promjena providera: samo admin, i novi provider mora voditi resurse

`PUT .../resources/{resourceId}/provider` smije samo admin. Provider ne može resurs prebaciti na drugoga, jer bi tako trener mogao nekome "podmetnuti" svoj resurs. Novi provider mora biti član organizacije s ulogom `PROVIDER` ili `ADMIN`. Složeni ključ iz `resources_provider_clan` čuva članstvo, ali klijenta bi pustio, pa se uloga provjerava u servisu. Ne-člana bi baza odbila, ali kao `500`, pa servis i njega uhvati ranije i vrati `400` s poljem `providerId`, u istom obliku kao greška validacije. Privremeno sam pustio svaku ulogu i test s klijentom Dženanom kao providerom je pao.

## Rute bez organizacije u putanji: /api/resources/{resourceId}

Prva takva ruta je `GET /api/resources/{resourceId}`, a iste će biti radno vrijeme, izuzeci i slobodni termini. Organizacija se tu dobija iz resursa: `@resourceAccess.canView` učita resurs, pa za njegovu organizaciju provjeri ulogu. Vidi ga svaki član, a klijent samo aktivan resurs, isto kao u listi. Nepostojeći resurs daje `403`, kao i nepostojeća organizacija.

Čuvar ruta u testu izolacije sada hvata i putanje pod `/api/resources/{...}`. Kad sam novu rutu izbacio iz liste testa, čuvar je pao. Test je morao i drugačije puniti putanje: varijable se sada popunjavaju po imenu (`id`, `resourceId`), a ne po redu, jer `/api/resources/{resourceId}` nema organizaciju na prvom mjestu.

`ResourceAccessTest` prolazi sve kombinacije bez HTTP-a: admin, trener svog i tuđeg resursa, trener kojem je uloga promijenjena u klijenta, klijent s aktivnim i deaktiviranim resursom, admin druge organizacije i neprijavljen korisnik.

## Radno vrijeme: lokalno vrijeme, bez preklapanja, i to čuva baza

Radno vrijeme resursa su sedmični intervali (`availability_rules`): dan u sedmici, početak i kraj kao `TIME`, bez datuma i bez zone. Vrijeme je lokalno u zoni organizacije. Da sam ga čuvao u UTC-u, ponedjeljak 08:00 u Sarajevu bi zimi bio 07:00 UTC, a ljeti 06:00 UTC, pa bi se pravilo pomjerilo za sat na prelasku na ljetno vrijeme. U UTC se pretvara tek kad se računaju termini za konkretan datum, što dolazi u koraku 5. Odgovor zato uz pravila vraća i zonu organizacije.

Dan je broj od 1 do 7 po ISO redoslijedu, isto kao `java.time.DayOfWeek` (1 je ponedjeljak). `EnumType.ORDINAL` bi ponedjeljak upisao kao 0, a nedjelju kao 6, i to ne bi odgovaralo `DayOfWeek.getValue()`.

Jedan dan može imati više intervala, npr. 08-12 i 13-16 s pauzom za ručak, ali se ne smiju preklapati. To čuva `EXCLUDE USING gist` constraint, isti mehanizam koji Faza 4 koristi za rezervacije. Postgres nema raspon za `TIME`, pa migracija pravi svoj (`CREATE TYPE timerange AS RANGE (subtype = time)`), po primjeru iz Postgres dokumentacije o range tipovima. Kraj je isključen, pa se 08-12 i 12-16 ne preklapaju. Na Neon-u ovo nisam provjerio. Lokalno i u testovima radi na Postgresu 17, a Neon je isti Postgres 17, ali deploy će to pokazati prvi.

Ista provjera postoji i u servisu, jer bi iz baze greška stigla kao `500` bez imena polja. Kad sam privremeno isključio provjeru u Javi, preklapanje je i dalje bilo odbijeno, ali kao `500`. Odgovor je sada `400` s poljem `rules[1]`. Rad preko ponoći (22:00-02:00) se ne može upisati kao jedan interval, nego kao dva, u dva dana. Isto tako nema intervala do ponoći: Java `LocalTime` ne zna za 24:00, pa je najkasniji kraj 23:59.

`PUT` zamijeni cijelu sedmicu: briše stara pravila i upisuje nova. Brisanje ide JPQL `delete` upitom koji ide u bazu odmah, iz istog razloga kao kod pozivnica u Fazi 2 (Hibernate pri flushu radi INSERT prije DELETE). Dva istovremena `PUT`-a bi se ispreplitala, pa servis prvo zaključa red resursa (`SELECT ... FOR UPDATE`). Test s 8 niti, gdje svaka šalje drugi interval, sa zaključavanjem ostavi tačno 1 interval. Kad sam zaključavanje privremeno izbacio, ostalo je 8, mješavina svih zahtjeva.

Radno vrijeme vide svi koji vide resurs, a mijenjaju ga admin i provider koji ga vodi, iste provjere kao za resurs. Vrijeme se u JSON-u vraća kao `"08:00:00"`, sa sekundama, a prima se i `"08:00"`.

## Izuzeci od radnog vremena: trenuci, ne lokalno vrijeme

Izuzetak (`time_off`) je konkretan period, npr. godišnji od 1. do 15. jula 2035. Zato su početak i kraj `TIMESTAMPTZ` (u Javi `Instant`), a ne lokalno vrijeme kao kod sedmičnog radnog vremena. Pravilo "ponedjeljkom 08-16" važi svake sedmice i mora ostati 08 po lokalnom satu i zimi i ljeti, a izuzetak se desi jednom i ima tačan početak.

API prima `OffsetDateTime`, pa vrijeme mora imati pomak (`2035-07-01T08:00:00+02:00`). Vrijeme bez pomaka se odbija sa `400`, jer se ne zna da li je to 08:00 u Sarajevu ili u UTC-u. Odgovor vraća UTC (`2035-07-01T06:00:00Z`), a u zonu organizacije prevodi frontend. Kraj mora biti poslije početka, i porede se trenuci, ne sat iz teksta: `10:15+02:00` je prije `10:00+01:00`. Kad sam privremeno poredio lokalni sat (`toLocalDateTime`), oba testa za to su pala. Isto pravilo čuva i `CHECK` u bazi.

Izuzeci se smiju preklapati (godišnji i praznik u isto vrijeme), jer to ne kvari ništa: termin je zatvoren ako ga pokriva bilo koji izuzetak.

Listu izuzetaka, kao i dodavanje i brisanje, vide samo admin i provider resursa, ne i klijent. Razlog može biti lična stvar, npr. bolovanje. Klijent će u koraku 5 samo vidjeti da u tom periodu nema slobodnih termina. Lista prikazuje izuzetke koji još traju ili tek dolaze (`ends_at` poslije sada, sat iz `Clock`-a), a prošli se ne prikazuju, ali ostaju u bazi. Brisanje traži par (izuzetak, resurs iz putanje), pa se izuzetak sale ne može obrisati kroz putanju saune, i kad korisnik uređuje oba.

## Slobodni termini se računaju, ne čuvaju

Slobodni termini nisu redovi u bazi. Računaju se iz radnog vremena, trajanja termina i izuzetaka za traženi period. Da sam ih unaprijed upisivao u tabelu, svaka promjena radnog vremena bi tražila brisanje i ponovno pravljenje budućih redova, i trebalo bi odlučiti koliko mjeseci unaprijed. Račun je u običnoj klasi `SlotCalculator`, bez Springa i bez baze, pa se sve provjerava unit testom za manje od sekunde.

Za svaki datum se lokalno vrijeme pravila pretvara u trenutak s pomakom koji važi baš tog datuma (`ZonedDateTime.of(datum, 08:00, zona)`). Ponedjeljak 08:00 u Sarajevu je zato 07:00 UTC prije 28.3.2027, a 06:00 UTC poslije, i po satu na zidu je oba puta 08:00. Termini se onda ređaju po stvarnom vremenu (`Instant` plus trajanje), a ne po satu na zidu. U noći 25.10.2026, kad se sat vrati s 03:00 na 02:00, pravilo od 00:00 do 04:00 traje pet stvarnih sati i daje pet satnih termina, a 28.3.2027. daje tri, jer sat od 02:00 do 03:00 ne postoji. Svaki termin uvijek traje tačno onoliko koliko piše na resursu.

Kad sam privremeno uzimao pomak zone za trenutak upita (`getOffset(now)`) umjesto za datum termina, pala su sva četiri DST testa, a zimski test i test sa zonom `Asia/Tokyo` su prošli. Ta greška bi zato prošla nezapaženo sve do prvog prelaska na ljetno vrijeme. Pravilo koje počinje baš u satu koji ne postoji (npr. 02:30 te martovske noći) nisam testirao. Po Javadoc-u za `ZonedDateTime.of` takvo vrijeme se pomjeri naprijed za dužinu rupe, na 03:30.

Termin se nudi samo ako cijeli stane u radno vrijeme (08:00-10:30 sa satnim terminima daje 08 i 09) i ako počinje poslije sada. Izuzetak zatvara termin samo ako se stvarno preklapaju, a kraj je isključen kao kod `tstzrange`. Izuzetak koji završava u 11:00 ne dira termin od 11:00. Kad sam poređenje privremeno promijenio da uključuje krajeve, taj test je pao.

Rezervacije se još ne oduzimaju, jer ih nema do Faze 4, a endpoint koji ovo vraća dolazi u sljedećem koraku.

## Endpoint za slobodne termine: datumi, najviše 31 dan

`GET /api/resources/{resourceId}/slots?from=2035-03-19&to=2035-03-27` prima datume, ne trenutke. Datumi su u zoni organizacije, a `to` se ne uključuje, pa je jedan dan `from=16.11.` i `to=17.11.`. Kalendar ionako traži dane ili sedmice, a sa trenucima bi frontend morao sam računati ponoć u Sarajevu. Period može biti najviše 31 dan, pa nema ni paginacije. Satni termini kroz mjesec dana su par stotina stavki, a i najkraći termin od 5 minuta, 24 sata dnevno, ostaje ispod 9000.

Termine vidi svako ko vidi resurs, i klijent, ista provjera `canView` kao za radno vrijeme. Iz baze se učitavaju samo izuzeci koji dodiruju traženi period, i oni koji su počeli prije njega. Kad sam privremeno uzimao samo izuzetke koji počinju unutar perioda, godišnji koji je počeo dan ranije nije zatvorio termin i test je pao. Deaktiviran resurs admin i provider i dalje vide, ali bez ijednog termina, jer se na njemu ne može rezervisati. Bez te provjere je test za deaktiviran resurs pao.

Nedostajući parametar i datum u pogrešnom obliku (`29.01.2035`) Spring je vraćao kao `400` s porukom na engleskom. Sada `GlobalExceptionHandler` za oba vraća isti oblik kao greška validacije (`greske: [{polje, poruka}]`), na bosanskom. Isti override hvata i UUID u pogrešnom obliku u putanji, ali to nisam posebno testirao.

Test izolacije je za ovu rutu morao slati i ispravne parametre. Obavezni parametri se, kao i tijelo, provjeravaju prije `@PreAuthorize`, pa bi ruta bez njih dala `400` i sakrila da provjera pristupa fali. Kad sam `@PreAuthorize` privremeno izbacio, test je pao na `/slots`.
