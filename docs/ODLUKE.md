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
- Ako dva admina u istom trenutku pozovu isti email, oba obrišu staru (koje nema) i oba upisuju. Jedan dobije `500` od indeksa. Pravilo u bazi ostaje tačno, ali odgovor nije lijep. Nisam to testirao. (Riješeno u Fazi 5, vidi "Pozivnice emailom".)
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

Rezervacije se tada još nisu oduzimale, jer ih nije bilo do Faze 4. Od Faze 4 se oduzimaju, vidi "Slobodni termini bez zauzetih" ispod.

## Endpoint za slobodne termine: datumi, najviše 31 dan

`GET /api/resources/{resourceId}/slots?from=2035-03-19&to=2035-03-27` prima datume, ne trenutke. Datumi su u zoni organizacije, a `to` se ne uključuje, pa je jedan dan `from=16.11.` i `to=17.11.`. Kalendar ionako traži dane ili sedmice, a sa trenucima bi frontend morao sam računati ponoć u Sarajevu. Period može biti najviše 31 dan, pa nema ni paginacije. Satni termini kroz mjesec dana su par stotina stavki, a i najkraći termin od 5 minuta, 24 sata dnevno, ostaje ispod 9000.

Termine vidi svako ko vidi resurs, i klijent, ista provjera `canView` kao za radno vrijeme. Iz baze se učitavaju samo izuzeci koji dodiruju traženi period, i oni koji su počeli prije njega. Kad sam privremeno uzimao samo izuzetke koji počinju unutar perioda, godišnji koji je počeo dan ranije nije zatvorio termin i test je pao. Deaktiviran resurs admin i provider i dalje vide, ali bez ijednog termina, jer se na njemu ne može rezervisati. Bez te provjere je test za deaktiviran resurs pao.

Nedostajući parametar i datum u pogrešnom obliku (`29.01.2035`) Spring je vraćao kao `400` s porukom na engleskom. Sada `GlobalExceptionHandler` za oba vraća isti oblik kao greška validacije (`greske: [{polje, poruka}]`), na bosanskom. Isti override hvata i UUID u pogrešnom obliku u putanji, ali to nisam posebno testirao.

Test izolacije je za ovu rutu morao slati i ispravne parametre. Obavezni parametri se, kao i tijelo, provjeravaju prije `@PreAuthorize`, pa bi ruta bez njih dala `400` i sakrila da provjera pristupa fali. Kad sam `@PreAuthorize` privremeno izbacio, test je pao na `/slots`.

## Slika resursa: kroz backend, tip po bajtovima, prvo fajl pa baza

U bazi je samo ključ slike (`image_key`), a sama slika je u skladištu iza interfacea `StorageService`. Interface ovdje ima smisla jer stvarno postoje dvije implementacije: folder na disku za lokalni rad i testove, i S3-kompatibilno skladište, jer Render nema trajni disk i slike bi nestale sa svakim deployem. Za sada postoji samo lokalna (korak 7a). Implementacija se bira preko `slotbook.storage.type`, a podrazumijevana je `local`.

Slika se čita preko `GET /api/resources/{resourceId}/image`, kroz backend i uz istu provjeru `canView` kao za resurs, a ne kao javni link na skladište. Javni link bi zaobišao izolaciju organizacija. Cijena je što frontend ne može staviti adresu u `<img src>`, jer taj zahtjev ne šalje `Authorization` header, pa će sliku dohvatiti `fetch`-om i prikazati kao blob. Druga opcija su potpisani linkovi koji ističu (presigned URL), ali to je dodatni koncept, a za demo slike od par stotina KB je nepotrebno.

Tip slike se određuje po prvim bajtovima fajla (JPEG, PNG, WebP), a ne po imenu ni `Content-Type` zaglavlju, jer njih šalje klijent. HTML preimenovan u `sala.png` dobija `400`. Kad sam privremeno pustio svaki fajl kao PNG, taj test je pao. Provjerava se samo potpis na početku, slika se ne dekodira, pa fajl s ispravnim potpisom i smećem iza prolazi. Za prikaz to nije opasno, jer se uvijek vraća s tipom slike, nikad kao HTML.

Upload nije u transakciji, jer skladište nije dio transakcije baze. Redoslijed je zato namjeran: prvo se upiše nova slika, pa ključ u bazi, pa se briše stara. Ako pukne upis u bazu, ostane slika koju niko ne koristi, ali nikad resurs koji pokazuje na sliku koje nema. Svaka slika dobija novi ključ s UUID-om, pa se stara i nova nikad ne prepišu. Kad sam brisanje stare privremeno izbacio, u skladištu su ostala dva fajla i test je pao. Dva istovremena uploada na isti resurs mogu ostaviti jednu sliku bez vlasnika. To nisam rješavao, jer je jedina posljedica fajl viška.

Najveća slika je 2 MB. Provjerava je servis, a postavljena je i u `spring.servlet.multipart`, da Tomcat odbije prevelik zahtjev prije nego što ga cijelog pročita. Granicu servisa pokriva API test. Tomcatovu granicu MockMvc ne primjenjuje, pa za nju postoji samo unit test koji pokazuje da odgovor bude `413` s porukom na bosanskom. Da je Tomcat stvarno odbije, nisam provjerio.

Lokalni folder je `uploads/` (u `.gitignore`), a testovi pišu u `target/test-uploads`. Bez `@PreAuthorize` na uploadu Bojan iz druge organizacije je dobio `200` i stvarno postavio sliku na sto u tuđoj čitaonici. Test izolacije sada šalje i multipart zahtjev s ispravnom slikom i provjerava da se ključ slike nije promijenio.

## S3 skladište: AWS SDK v2, test na S3Mock umjesto MinIO

`S3StorageService` koristi AWS SDK za Javu v2 (`software.amazon.awssdk:s3`, verzija 2.57.5). R2 i ostala S3-kompatibilna skladišta govore isti API, pa poseban klijent ne treba. Uzeo sam verziju od 27.10.2026, a ne 2.59.0 od 12.11., jer je ova druga izašla dan ranije. Klijent je podešen po Cloudflare primjeru za Java SDK: `region("auto")`, bucket u putanji (`pathStyleAccessEnabled`) i `chunkedEncodingEnabled(false)`. Bez ovog zadnjeg, po Cloudflare dokumentaciji, R2 odbija `putObject` sa `403`, jer potpis ne odgovara tijelu koje se šalje u dijelovima. Na samom R2 to nisam provjerio, jer R2 još nije podešen (Faza 12).

Za test sam prvo dodao Testcontainers modul za MinIO, a onda se pokazalo da MinIO više nema javnih Docker slika: `minio/minio` ne postoji na Docker Hubu, a `quay.io` traži prijavu. Test zato radi na `adobe/s3mock:5.2.3`, običnom `GenericContainer`-u, bez dodatnog modula. Test dokazuje da upis, čitanje i brisanje rade preko S3 API-ja. Ne dokazuje da radi i s R2, i ne može pasti zbog `chunkedEncodingEnabled`, jer S3Mock prima i tijelo u dijelovima.

Implementacija se bira preko `slotbook.storage.type`, a `StorageSelectionTest` pokazuje da se bez podešavanja bira disk, a uz `s3` S3. Podaci za S3 (`S3_ENDPOINT`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`) nemaju podrazumijevane vrijednosti. S tipom `s3` aplikacija bez njih ne starta, i za to postoji test. Prvi put je taj test prolazio iz pogrešnog razloga: mali Spring kontekst u testu nepoznat `${...}` ostavi kao tekst, pa je padao `URI.create`. Sad test učita isti mehanizam za placeholdere kao aplikacija i provjerava poruku o placeholderu. Kad je tip `local`, S3 podaci se ne čitaju, pa lokalno i u testovima ne trebaju.

Dok R2 ne bude podešen, Render radi s tipom `local`, pa slike nestanu sa svakim deployem ili restartom. Za demo to još nije bitno, jer frontenda nema.

## Rezervacije: djelimičan EXCLUDE, klijent bez FK na članstvo

Tabela `bookings` ima `EXCLUDE` constraint s `WHERE (status IN ('PENDING', 'CONFIRMED'))`. Bez tog `WHERE` bi otkazana, odbijena ili istekla rezervacija zauvijek držala svoj termin. Kad sam `WHERE` privremeno izbacio, pao je test u kojem preko otkazane rezervacije ide nova. Kad sam raspon privremeno napravio s uključenim krajem (`'[]'`), pao je test da 09-10 i 10-11 mogu zajedno. Na Neon-u sam 14.11. napravio probnu tabelu s istim constraintom: preko `CANCELLED` reda je prošao `CONFIRMED`, a preklapanje 09:30-10:30 je palo sa `23P01`. Tabelu sam onda obrisao, da Flyway može sam izvršiti V10.

Preklapanje se poredi po trenutku, ne po satu iz teksta. `08:30+00` i `09:30+01` su isti trenutak i odbijaju se, za to postoji test.

`client_id` ide na `users`, a ne složenim FK na `organization_members` kao kod providera. Takav FK bi u bazi garantovao da je klijent član organizacije, ali bi `bookings` morao nositi i `organization_id`, a izlazak iz organizacije ne bi mogao obrisati red članstva dok postoje stare rezervacije. Članstvo je pravilo u trenutku rezervacije, pa ga provjerava servis. Za razliku od preklapanja, to nije nešto što mora važiti zauvijek.

Resurs ima `auto_confirm` (V11). Kad ga nema u zahtjevu, uzima se `true`, i kod `POST` i kod `PUT`, isto kao što opis koji nije poslan postane prazan. Postojeći resursi su migracijom ostali na automatskoj potvrdi, jer su do sada tako i radili.

Rezervacija prima samo početak. Servis pita `SlotService.offered` je li to jedan od termina koje kalendar nudi (poravnat na trajanje, u radnom vremenu, izvan izuzetaka, u budućnosti), a kraj uzima iz tog termina. Tako postoji jedan račun termina, a ne dva koja bi se mogla razići. Zauzetost `offered` namjerno ne gleda. Kad bi gledao, drugi od dva istovremena zahtjeva bi nekad dobio `400` "termin se ne nudi" umjesto `409`, zavisno od toga da li je prvi već commitan. Zauzet termin odbija baza, a servis grešku sa SQLSTATE `23P01` pretvara u `409`. Druge greške integriteta ostaju `500`, da se npr. pokvaren FK ne prijavi kao zauzet termin. Kad sam privremeno izbacio to prevođenje, test za zauzet termin je pao, a kad sam preskočio provjeru ponuđenog termina, pala su dva testa za neispravan početak.

Historija statusa (`booking_events`, V12) se upisuje u istoj transakciji kao promjena, pa ne postoji rezervacija bez prvog zapisa. Nastanak je zapis bez `from_status`, i baza preko `CHECK`-a pušta samo nastanak u `PENDING` ili `CONFIRMED`. Kolonu `details` iz skice nisam napravio, jer je još ništa ne koristi.

## 20 istovremenih rezervacija: EXCLUDE sam nije bio dovoljan

`BookingIstovremenoApiTest` šalje kroz pravi HTTP server (ne kroz MockMvc, koji ne obećava rad iz više niti) 20 zahtjeva u istom trenutku za isti termin, od 20 različitih studenata. Prvo pokretanje: tačno jedna rezervacija u bazi, ali 19 odgovora `500`. Svih 19 je palo sa SQLSTATE `40P01`, `deadlock detected ... while checking exclusion constraint on tuple (0,1) in relation "bookings"`. Kod `EXCLUDE` constrainta Postgres prvo upiše red pa traži sukobe, pa dvije transakcije koje upišu u isto vrijeme nađu jedna drugu i čekaju jedna na drugu. To piše i u izvoru Postgresa, u komentaru na vrhu `src/backend/executor/execIndexing.c` ("There is a chance of deadlock, if two backends insert a tuple at the same time"). Pretpostavio sam da drugi zahtjev uvijek dobije `23P01`, kao kod `UNIQUE`, i to nije bilo tačno.

Birao sam između tri rješenja: zaključati red resursa prije upisa, prevesti `40P01` u `409`, ili ponoviti transakciju poslije deadlocka. Uzeo sam prvo. `BookingService.create` prvo radi `SELECT ... FOR UPDATE` nad resursom (isti `findLockedById` kao kod radnog vremena), pa rezervacije istog resursa idu jedna za drugom, a drugi zahtjev vidi commitan red i dobije `23P01`. Preklapanje i dalje čuva `EXCLUDE`. Lock je tu zbog čiste greške, a ne kao zaštita. Cijena je da i rezervacije različitih termina istog resursa čekaju jedna drugu. Prevođenje `40P01` u `409` bi pretpostavljalo da je svaki deadlock zbog preklapanja, a ponavljanje bi tražilo kod izvan transakcije.

Deadlock zavisi od tajminga. Bez locka je jedna runda od 20 niti pala u prva dva pokretanja, a u sljedeća dva je prošla. Zato test sada ima 4 runde, svaku na svoj termin. Sa lockom je prošao u svakom pokretanju, a svih 76 odbijenih zahtjeva dobije `23P01`. Bez locka je pao u oba pokretanja s 4 runde, svaki put s jednom rundom od 19 × `40P01`. Da test bez locka padne svaki put, ne mogu garantovati. Sam paralelni dio sa lockom traje 0,6-0,7 s za jednu rundu na mom laptopu, a test u cjelini oko minut, najviše zbog starta konteksta i 21 BCrypt registracije.

## Slobodni termini bez zauzetih: SlotController prelazi u booking

`GET /api/resources/{id}/slots` sada od ponuđenih termina oduzima one koje drži `PENDING` ili `CONFIRMED` rezervacija. Otkazane i ostale ne smetaju, i za to postoji test, koji je pao kad sam privremeno uzimao rezervacije u svim stanjima. Lista stanja koja drže termin je na jednom mjestu u Javi (`BookingStatus.DRZE_TERMIN`), i ista je kao `WHERE` u constraintu.

Rezervacija već zavisi od `availability` (pita `SlotService.offered`), pa da je `SlotService` počeo čitati rezervacije, paketi bi zavisili jedan od drugog. Nisam htio interface u `availability` koji bi implementirao `booking`, jer bi to bila apstrakcija samo da se zaobiđe zavisnost. Umjesto toga je `SlotController` prešao u `booking`, uz `FreeSlotService` koji uzme ponuđene termine i izbaci zauzete. `availability` zna šta nude pravila, a `booking` šta je od toga slobodno. Adresa endpointa se nije promijenila.

Upit za zauzete termine je običan izvedeni upit (`startTime < do` i `endTime > od`). Ne koristi gist indeks iz constrainta, jer taj indeks radi nad `tstzrange(start_time, end_time)`. Da li to smeta, gledam u Fazi 9 s `EXPLAIN`, a ne sada.

## Idempotency-Key: isti ključ vraća istu rezervaciju

Klijent uz `POST .../bookings` smije poslati zaglavlje `Idempotency-Key` (1 do 100 znakova, frontend će slati novi UUID za svaki klik na "Rezerviši"). Ključ je u koloni `idempotency_key`, a `UNIQUE (client_id, idempotency_key)` ga čini jedinstvenim po klijentu, ne globalno. Dvije studentice koje slučajno pošalju isti ključ dobiju svaka svoju rezervaciju, i to ima test. Bez ključa sve radi kao prije, jer `UNIQUE` ne poredi `NULL`.

Ponašanje sam uzeo iz IETF nacrta `draft-ietf-httpapi-idempotency-key-header-08`, odjeljak 2.7. Isti ključ za isti resurs i početak vraća raniju rezervaciju, opet sa `201`, u stanju u kojem je sada. Isti ključ za drugi termin je `422`, kako nacrt preporučuje. Nacrt za zahtjev s ključem koji se još obrađuje predlaže `409`. Kod mene duplikat čeka lock resursa iz koraka sa 20 niti, pa umjesto `409` dobije gotov odgovor. Rok trajanja ključa nisam uveo, ključ vrijedi dok postoji rezervacija.

Redoslijed je bitan: prvo lock resursa, pa tek onda traženje ranije rezervacije s ključem. Test šalje 10 istovremenih zahtjeva istog studenta s istim ključem i očekuje 10 puta `201` s istim id-jem. Kad sam traženje privremeno stavio prije locka, dobio sam 1 × `201` i 9 × `409`, što bi korisniku za vlastiti dupli klik prikazalo "termin je zauzet". Isti ključ u istom trenutku za dva različita resursa lock ne poreda, pa tada odlučuje `UNIQUE` i drugi dobije `422`. Taj slučaj nisam testirao.

## Prelazi stanja: pravilo u entitetu, @Version protiv izgubljene izmjene

Svaki prelaz ima svoju adresu (`POST /api/bookings/{id}/confirm`, `/reject`, `/cancel`, `/check-in`, `/no-show`), a ne `PUT` sa statusom u tijelu. Tako svaki prelaz ima svoje pravilo ko ga smije: potvrdu, odbijanje, dolazak i nedolazak radi admin ili provider resursa (`BookingAccess.canManage`), a otkazati može i klijent, ali samo svoju rezervaciju (`canCancel`). Koji prelaz je dozvoljen čuva `Booking.prijedi`, pa ga ne može zaobići nijedan servis. Ostali daju `409` sa trenutnim i traženim stanjem u odgovoru. Unit test prođe svih 49 parova stanja i očekuje tačno 7 prelaza iz tabele u planu. Nedolazak se ne može označiti prije početka termina, i to je isto `409`. To pravilo nije bilo u tabeli, dodao sam ga jer nedolazak prije termina nema smisla.

Istovremenu izmjenu iste rezervacije (profesorica potvrđuje dok je studentica otkazuje) čuva `@Version` (V14), dakle optimistično zaključavanje. Hibernate u `UPDATE` doda `where version = ?`, pa zastarjela izmjena ne nađe red i pada, a klijent dobije `409` "Rezervaciju je u međuvremenu promijenio neko drugi". Bez toga bi oba zahtjeva pročitala `PENDING`, oba bi prošla, a u historiji bi bila dva prelaza iz `PENDING`. Test to radi bez niti: otvori rezervaciju u jednoj transakciji, otkaže je u drugoj (`REQUIRES_NEW`) pa pokuša potvrditi staru kopiju. Bez `@Version` je pao.

Za kreiranje sam koristio pesimistični lock resursa, a ovdje optimistični. Kod kreiranja se sudaraju upisi u `EXCLUDE`, pa je čekanje u redu jedini način da drugi dobije čistu grešku. Kod prelaza su sudari rijetki, jer su to klikovi ljudi, a i kad se dese, `409` s porukom da se osvježi prikaz je pošten odgovor. Cijena: ako bi drugi prelaz i nad novim stanjem bio dozvoljen (otkazivanje upravo potvrđene rezervacije), korisnik ga mora ponoviti. Sa `SELECT ... FOR UPDATE` bi taj drugi zahtjev sačekao i prošao.

Kolone `cancelled_at` i `cancel_reason` iz skice nisam napravio. Vrijeme otkazivanja i ko je otkazao već stoje u `booking_events`, a razlog dolazi ako ga UI bude tražio.

## Rok za otkazivanje važi samo za klijenta

Resurs ima `cancel_notice_hours` (V15), od 0 do 168 sati, a podrazumijevano je 0, dakle bez roka. U skici se kolona zvala `min_notice_hours`, ali iz tog imena se ne vidi da je rok za otkazivanje, a ne za rezervaciju, pa sam je preimenovao. Klijent otkazuje najkasnije toliko sati prije početka: tačno na rok još može, sekundu poslije ne, i za obje granice postoji unit test. Poslije roka dobija `409` "Rok za otkazivanje je prošao", sa rokom u odgovoru, da ga frontend prikaže u zoni organizacije. Admin i provider resursa otkazuju bez roka, jer otkazuju i zbog bolesti ili kvara. Ko vodi resurs utvrđuje se istom provjerom kao za potvrdu (`BookingAccess.canManage`). Kad sam rok privremeno isključio, pala su dva testa, a kad kontroler nikad nije javljao da korisnik vodi resurs, pao je test da profesorica otkazuje i poslije roka.

## Moje rezervacije i historija rezervacije

`GET /api/bookings/mine` vraća rezervacije prijavljenog korisnika iz svih organizacija, po stranicama. Redoslijed bira server: najkasniji termin prvo, pa id, isto kao kod resursa, gdje se sort iz zahtjeva ignoriše. Tako budući termini idu prije prošlih. Posebnog filtera za "samo buduće" nema, to ću dodati ako ga frontend bude trebao. Uz svaku rezervaciju ide naziv resursa i zona organizacije, jer klijent ima rezervacije u više organizacija, a vrijeme se prikazuje u zoni organizacije. Resurs i organizacija se učitaju istim upitom (`join fetch`), a ne po jedan upit za svaki red. Indeks `(client_id, start_time)` je u V16. Kad sam iz upita privremeno izbacio uslov za klijenta, pao je test u kojem Hana ne smije vidjeti Lamijinu rezervaciju.

`GET /api/bookings/{id}/events` vraća historiju jedne rezervacije, redom, s imenom osobe koja je promijenila status. Vidi je klijent te rezervacije i ko vodi resurs, ista provjera kao za otkazivanje (`BookingAccess.canView`). Ovo je jedina lista bez stranica, jer rezervacija ima nastanak i najviše dva-tri prelaza.

## EXCLUDE ili SELECT ... FOR UPDATE: šta je na kraju ispalo

U planu je pisalo da biram između `EXCLUDE` constrainta i pesimističnog zaključavanja. Na kraju koristim oba, svako za drugu stvar.

`EXCLUDE` je garancija. Dvije aktivne rezervacije istog resursa se ne mogu preklopiti ni kad red upiše neko mimo Jave, ručno ili kroz budući kod koji zaboravi provjeru. Da sam imao samo `FOR UPDATE`, ta garancija bi zavisila od toga da svaki put koji upisuje rezervaciju prvo zaključa resurs, a to baza ne može provjeriti.

`FOR UPDATE` nad resursom je tu zbog odgovora klijentu. Bez njega je test sa 20 niti dobijao deadlock (`40P01`) i `500` umjesto `409`, jer kod `EXCLUDE` Postgres prvo upiše red pa traži sukobe. Sa lockom rezervacije istog resursa idu jedna za drugom, drugi zahtjev vidi commitan red, dobije `23P01` i vrati se `409`. Isti lock rješava i dupli klik s `Idempotency-Key`, jer duplikat sačeka prvi zahtjev i nađe njegovu rezervaciju.

Cijena je da sve rezervacije istog resursa čekaju jedna drugu, i za različite termine. Jedna rezervacija drži lock par milisekundi, a za jedan resurs (sto, salu, konsultacije) to nije usko grlo. Nisam mjerio pod opterećenjem, to dolazi s k6 testom u Fazi 9.

## Rok za potvrdu: 24 sata, najkasnije početak termina

Rezervacija na ručnoj potvrdi drži termin dok čeka providera. Bez roka bi profesorica koja ne otvori aplikaciju sedmicu dana držala termine zauzetim, a niko drugi ih ne bi mogao uzeti. Zato `PENDING` rezervacija dobija `expires_at` u trenutku nastanka: 24 sata (`slotbook.booking.pending-hours`), a ako termin počinje ranije, onda početak termina. Rok se upisuje u bazu i ne mijenja se, pa pozadinski posao koji dolazi u sljedećem koraku samo traži `PENDING` s prošlim rokom.

Rok je isti za sve resurse. Mogao je biti na resursu, kao rok za otkazivanje, ali za to još nemam pravi razlog. Baza čuva dvije stvari: `PENDING` mora imati rok (`bookings_pending_ima_rok`), i rok ne smije biti poslije početka termina (`bookings_rok_do_pocetka`). Postojeće `PENDING` rezervacije je migracija dopunila istim pravilom. Poslije potvrde rok ostaje u bazi, ali ga API više ne vraća.

## Istek i zatvaranje: jedna SQL naredba, a ne entitet

Pozadinski posao (`BookingJob`, svake minute) ne učitava rezervacije u Javu da na svakoj pozove `prijedi`. Jedan `UPDATE ... RETURNING` promijeni status, a `INSERT` u istoj naredbi upiše historiju, s praznim akterom jer promjenu nije napravio korisnik. Baza dozvoljava prazan akter samo za `EXPIRED` i `COMPLETED` (`booking_events_akter`).

Razlog je istovremenost s providerom. Kad provider potvrđuje baš kad posao radi, `UPDATE` sačeka njegov red i ponovo provjeri `status = 'PENDING'`, pa potvrđenu ne dira. Ako posao stigne prvi, poveća `version`, pa providerova potvrda padne na `@Version` i dobije `409`. To drugo dokazuje test `kasnaPotvrdaNePrepisujeIstek`. Prvi slučaj nisam testirao, oslanjam se na to kako Postgres u `READ COMMITTED` ponovo provjeri `WHERE` poslije čekanja na red. Cijena je da pravilo prelaza za ova dva slučaja živi u SQL-u (`WHERE status = 'PENDING'`), a ne u `Booking.prijedi`.

Render na besplatnom planu uspava servis, a uspavan servis ne vrti posao. Istekla rezervacija bi tada i dalje držala termin, jer `EXCLUDE` ne zna za rok (u `WHERE` constrainta ne može stajati `now()`). Zato `BookingService.create`, odmah poslije locka resursa, prvo prebaci istekle rezervacije tog resursa u `EXPIRED`. Posao je tu za historiju i za resurse na koje niko ne rezerviše.

Potvrđena rezervacija se zatvara 24 sata poslije kraja (`slotbook.booking.complete-after-hours`), a ne odmah, jer je `COMPLETED` konačno stanje i provider poslije toga ne bi mogao označiti nedolazak. Dok se provider ne javi, rezervacija u statistici izgleda kao održana.

Provider i dalje može potvrditi rezervaciji kojoj je rok prošao, ako posao još nije stigao. Nisam to zabranio, jer bi kasna potvrda u tom slučaju bila korisna, a termin niko drugi nije uzeo (inače bi `create` već istekao rezervaciju).

ShedLock za sada ne treba, jer radi jedna instanca. I s dvije bi ovaj posao bio siguran iz istog razloga kao gore: druga naredba ne nađe redove koje je prva već promijenila. Nisam to probao s dvije instance.

## Obavijesti kroz outbox, bez @TransactionalEventListener

Email se ne šalje iz transakcije rezervacije. Da se šalje, spor SMTP bi držao lock resursa, a greška pri slanju bi poništila rezervaciju koja je inače dobra. Obrnuto je još gore: mail ode, a transakcija poslije padne, pa klijent dobije potvrdu za rezervaciju koje nema.

Zato `BookingService` u istoj transakciji upiše red u `notifications`, a poseban posao ga kasnije pošalje. Rezervacija i obavijest su tako ili obje u bazi ili nijedna. `NotificationService.enqueueForBooking` ima `Propagation.MANDATORY`, pa poziv bez transakcije pozivaoca baci izuzetak umjesto da obavijest upiše sama. Probao sam i `REQUIRES_NEW`: obavijest tada padne na FK, jer nova transakcija ne vidi rezervaciju koja još nije commitana.

Plan je spominjao `@TransactionalEventListener(AFTER_COMMIT)`. Taj listener se pokreće poslije commita, ali u memoriji, pa ako aplikacija padne između commita i slanja, obavijest je izgubljena i nigdje nema traga. Outbox tabela to rješava, pa listener za sada ne uvodim. Mogao bi kasnije poslužiti da se poslije commita odmah probudi slanje, umjesto da se čeka sljedeći krug posla, ali to je samo brzina, ne pouzdanost.

U planu je `notifications` imala `payload JSONB`. Za sada su sve obavijesti o rezervaciji, pa je dovoljan `booking_id`, a tekst emaila se pravi pri slanju iz rezervacije, resursa i zone organizacije. JSON bi ovdje bio još jedan format koji Hibernate mora znati čitati, a nisam provjerio kako to radi s Jacksonom koji ide uz naš Spring Boot. Pozivnice i reset lozinke će trebati nešto drugo, i tada odlučujem šta.

Primaoca bira `BookingService`: nova rezervacija na čekanju ide provideru resursa, potvrda, odbijanje i istek klijentu, a otkazivanje drugoj strani od one koja je otkazala. Dolazak, nedolazak i zatvaranje ne šalju ništa.

## Slanje emaila: tekst unaprijed, jedna obavijest po transakciji, SKIP LOCKED

Tekst emaila (`subject`, `body`) se piše kad obavijest nastane, u `BookingService`, gdje su rezervacija, resurs i zona organizacije pri ruci. Pošiljalac samo uzme red i pošalje ga. Da se tekst pravi pri slanju, paket `notification` bi morao čitati rezervacije, a `booking` već zna za `notification`, pa bi to bio krug. Usput mail kaže ono što je bilo tačno kad se desila promjena. Ovim je i `payload JSONB` iz plana za sada nepotreban.

Vrijeme u mailu je u zoni organizacije, formatirano s `Locale.forLanguageTag("bs")`. Nisam bio siguran da li Javin CLDR za bosanski daje "novembar" ili "novembra", pa sam to provjerio testom: dobija se "ponedjeljak, 19. novembar 2035. u 09:00".

`NotificationSender.sendNext` uzme jednu obavijest (`FOR UPDATE SKIP LOCKED`), pošalje je i upiše ishod, sve u jednoj transakciji. Red je zaključan dok traje slanje, pa ga drugi krug posla ili druga instanca preskoči umjesto da ga pošalje još jednom. To dokazuje test u kojem jedna transakcija drži red, a druga ga ne nađe. Bez `SKIP LOCKED` ista ta druga čeka i padne na `lock timeout`. Cijena: ako mail ode, a commit poslije padne, obavijest ostane `PENDING` i ode još jednom. Biram da mail ode dvaput, a ne nijednom.

Ponovni pokušaj je poslije 1, 5, 25 i 125 minuta, a poslije petog neuspjeha obavijest je `FAILED`, s porukom zadnje greške u `last_error`. Hvata se samo `MailException`. Druga greška je bug: transakcija se poništi, obavijest ostane kakva je bila i čeka popravku koda. To znači da bi takva obavijest blokirala ostale u istom krugu, jer bi se uvijek uzimala prva. Nisam to rješavao dok se ne desi.

SMTP ima timeout od 5 sekundi (`spring.mail.properties.mail.smtp.*timeout`). Bez toga JavaMail čeka beskonačno, a posao stoji.

U testovima je posao za slanje isključen preko `src/test/resources/application-default.properties`. Spring taj fajl učita kad nijedan profil nije aktivan, a postoji samo na classpathu testova. Bez toga bi svaki test koji napravi rezervaciju slao mail na `localhost:1025`, pa u lokalni Mailpit ako radi. Da je u produkciji uključen, provjerava `ProdProfilTest`.

Na Renderu SMTP još nije podešen. Obavijesti tamo padaju i poslije pet pokušaja ostaju `FAILED`, a rezervacije rade normalno. Pravi SMTP dolazi u Fazi 12.

Mail starter sam doda provjeru SMTP-a u `/actuator/health`. To sam otkrio tek kad je pao `SecurityApiTest`: health je vratio `503`, jer u testu nema SMTP-a. Na Renderu bi isto tako pao health check i novi deploy ne bi prošao. Zato je `management.health.mail.enabled=false`. Aplikacija je zdrava i kad mail ne radi, jer obavijesti čekaju u tabeli.

## Potvrda emaila: token u fragmentu linka, tijelo maila se briše poslije slanja

Token za potvrdu je isti `RandomToken` kao za refresh i pozivnice (32 nasumična bajta), a u `user_tokens` ide samo njegov SHA-256. Link važi 24 sata i može se iskoristiti jednom. Dva klika u istom trenutku rješava `UPDATE ... WHERE used_at IS NULL`, kao kod pozivnica. Tokeni su u paketu `auth`, ne u `user`, jer `notification` već zna za `user`, a `user` koji šalje obavijesti bi napravio krug.

Problem je bio što mail mora nositi pravi token, a mail iz outboxa čeka u tabeli `notifications`. Da tijelo ostane tamo, ko pročita bazu mogao bi potvrditi tuđi email, a u sljedećem koraku i resetovati tuđu lozinku. Zato tijelo postoji samo dok obavijest čeka slanje: kad je `SENT` ili `FAILED`, tijelo se briše, i to čuva baza (`notifications_tijelo_dok_ceka`). Važi za sve obavijesti, i za one o rezervaciji, jer mi poslan tekst ne treba, a manje ličnih podataka u bazi je bolje. Dok čeka, token je i dalje u bazi, obično manje od 30 sekundi. Kad SMTP ne radi, može čekati do dva i po sata, dok obavijest ne postane `FAILED`.

Link je `APP_URL/potvrda-emaila#token=...`. Dio poslije `#` preglednik ne šalje serveru, pa token ne završi u logovima hostinga frontenda ni u `Referer` zaglavlju. Stranica ga pročita i pošalje na `POST /api/auth/verify-email` u tijelu zahtjeva. Taj endpoint ne traži prijavu, jer se mail često otvori na telefonu gdje korisnik nije prijavljen.

Nalog i prvi mail nastaju u istoj transakciji (`EmailVerificationService.register`), pa nema naloga kojem link nije ni krenuo. Ako mail ne stigne, prijavljen korisnik traži novi (`POST /api/me/email-verification`). Stari linkovi ostaju važeći do svog roka. Za već potvrđen email odgovor je isti (`204`), samo se mail ne šalje. Ograničenje koliko često se smije tražiti novi link dolazi s rate limitingom u Fazi 8.

Nepotvrđen email za sada ništa ne zabranjuje: može se prijaviti i rezervisati. Šta tačno treba zabraniti (npr. prihvatanje pozivnice ili rezervaciju) odlučujem kad dođe frontend i vidim tok.

## Reset lozinke: isti mehanizam kao potvrda emaila, uz odjavu sa svih uređaja

Link za novu lozinku koristi istu tabelu `user_tokens` i isti token u fragmentu (`/nova-lozinka#token=...`). Logiku za izdavanje i jednokratno iskorištavanje sam izvukao u `UserTokenService`, da potvrda emaila i reset ne ponavljaju isti kod. Tip tokena je dio upita, pa link za potvrdu emaila ne može postaviti lozinku. Test to provjerava.

Link važi sat vremena, a ne 24 kao potvrda emaila, jer daje pristup nalogu. Kad se lozinka promijeni, dešavaju se još tri stvari, u istoj transakciji. Svi ostali neiskorišteni linkovi za reset tog korisnika postaju iskorišteni, inače bi stariji mail iz inboxa mogao ponovo promijeniti lozinku. Svi refresh tokeni korisnika se poništavaju, pa ko je znao staru lozinku ostaje bez sesije. Email se računa kao potvrđen, jer je link stigao baš na njega.

Access token koji je već izdat i dalje važi do 15 minuta. To nisam rješavao, jer bi tražilo provjeru u bazi na svakom zahtjevu, a 15 minuta je ionako rok koji sam prihvatio za odjavu.

`POST /api/auth/password-reset` vraća `204` i za email bez naloga, da se ne može provjeriti ko je registrovan. Za postojeći nalog server upiše token i obavijest, a za nepoznat email samo pročita bazu. Razlika u vremenu odgovora je vjerovatno nekoliko milisekundi. Nisam je mjerio. Kod prijave sam vrijeme izjednačio, jer je tamo razlika bila BCrypt od 100 ms. Ovdje je razlika mala, a i rate limiting iz Faze 8 ograničava koliko pokušaja neko ima. Isti rate limiting treba i da ovaj endpoint ne postane način da se nekome zatrpa inbox.

## Pozivnice emailom: obavijest bez korisnika, token više ne ide adminu

Pozivnica ide na email koji možda još nema nalog, a obavijest je do sada uvijek imala `user_id`. Mogao sam svakoj obavijesti upisati adresu primaoca, ali onda bi email svakog korisnika stajao u još jednoj tabeli, samo zbog jednog tipa obavijesti. Zato `user_id` može biti prazan i obavijest tada ima kolonu `email`. Baza dozvoljava samo jedno ili drugo, i to po tipu: pozivnica ide na adresu, a sve ostalo korisniku (`notifications_primalac`). Pošiljalac uzme adresu korisnika ili adresu iz obavijesti, ostatak slanja je isti.

`POST /api/organizations/{id}/invites` više ne vraća token. Link `APP_URL/pozivnica#token=...` dobije samo pozvana osoba, a stranica ga šalje na `POST /api/invites/accept` kao i do sada. Time je mnogo manji rizik iz Faze 2, da neko registruje tuđi email i prihvati pozivnicu poslanu na njega: za to sad treba pristup tom inboxu. Prihvatanje i dalje ne traži potvrđen email, jer token iz maila već dokazuje isto.

Kad admin pošalje novu pozivnicu na isti email, stara se briše, pa link iz starog maila vraća `404`. Prvo sam to ostavio tako, pa bi stari mail, ako još nije poslan, otišao s linkom koji ne radi. Onda sam obavijesti dodao `invite_id` s `ON DELETE CASCADE`: brisanje stare pozivnice briše i njen mail, a test provjerava da poslije zamjene čeka samo jedan, s novom ulogom. Usput nestane i red poslanog maila za staru pozivnicu, što mi ne smeta, jer tijela ionako nema. Ostaje uzak prozor: ako pošiljalac upravo šalje stari mail, brisanje čeka njegov commit i mail ipak ode.

Dva admina koja u istom trenutku pozovu isti email su ranije davala `500`, jer oba obrišu staru pozivnicu i oba upisuju novu, a parcijalni indeks pusti samo prvog. Sada se ta greška indeksa (prepoznata po imenu `organization_invites_jedna_otvorena`) prevodi u `409` s porukom da je pozivnica već poslana (prvo je pisalo "pogledaj listu pozivnica", a te liste nema, pa je poruka ispravljena 18.11.). Odluku i dalje donosi baza. Test s 8 niti u 5 krugova provjerava da na kraju postoji jedna pozivnica i jedan mail i da nijedna nit ne dobije drugu grešku. Bez prevođenja je pao s `duplicate key`. Sam `409` kroz HTTP nisam testirao, handler je isti kao ostali.

Naslov maila je "Pozivnica na SlotBook: Čitaonica PMF", a ne "Pozivnica u Čitaonica PMF", jer se naziv organizacije ne može deklinirati.

## Podsjetnik dan prije termina, najviše jedan po rezervaciji

Podsjetnik ide klijentu 24 sata prije potvrđenog termina (`slotbook.booking.reminder-hours`). Ne pravi se unaprijed s rokom slanja, nego ga `BookingJob` svake minute upiše za potvrđene rezervacije koje počinju u sljedećih 24 sata, a podsjetnik još nemaju. Tako otkazana ili odbijena rezervacija nikad ne dobije podsjetnik, a ne treba ga ni brisati. Ako posao kasni, podsjetnik ode kasnije, ali samo dok termin nije počeo.

Ko rezerviše manje od dan unaprijed, ne dobija podsjetnik, jer je potvrdu dobio maloprije. Inače bi neko ko u 10 rezerviše termin za 14 dobio dva maila u istom minutu.

Najviše jedan podsjetnik po rezervaciji čuva baza, parcijalnim UNIQUE indeksom nad `notifications (booking_id) WHERE type = 'BOOKING_REMINDER'`. Upit posla preskače rezervacije koje podsjetnik već imaju, pa se indeks u normalnom radu ne aktivira. Aktivira se kad dva posla krenu u isto vrijeme, npr. na dvije instance: drugi tada padne na indeksu, cijeli njegov krug se poništi i ponovi minutu kasnije. Test bez provjere u upitu pokazuje da baza odbije drugi podsjetnik (`duplicate key`). Da li uopšte puštati dvije instance, odlučujem s ShedLockom u zadnjem koraku Faze 5.

## Prilog .ics uz potvrdu rezervacije, pisan ručno

Potvrda rezervacije nosi `termin.ics` (iCalendar, RFC 5545). Fajl pišem sam u `BookingCalendar`, bez biblioteke: to su četiri polja (`UID`, `DTSTART`, `DTEND`, `SUMMARY`) i par pravila formata. Biblioteka poput ical4j bi bila veća od cijelog paketa `notification`.

Vrijeme ide u UTC-u, s `Z` na kraju (`DTSTART:20351119T080000Z`). Kalendar ga sam prikaže u zoni korisnika, pa ne treba blok `VTIMEZONE` s pravilima ljetnog vremena. Termin u 09:00 prije i poslije prelaska na ljetno vrijeme (30.3.2036) u fajlu je u 08:00Z i 07:00Z, a u kalendaru oba u 09:00. To provjerava test. Kad sam u mutaciji formatirao po sarajevskom vremenu, a ostavio `Z`, test je pao, jer bi kalendar termin pomjerio za sat ili dva.

Dva pravila formata sam provjerio testom, jer se lako promaše. Zarez i tačka-zarez u nazivu idu s `\` ispred, inače "Sala 2, prvi sprat" postane dvije vrijednosti. Red duži od 75 bajtova se lomi, a nastavak počinje razmakom. Broje se bajtovi, ne znakovi: č je dva bajta u UTF-8, pa bi brojanje znakova dalo predugačke redove. Lomi se između znakova, da se nijedno slovo ne prepolovi.

`UID` je ID rezervacije, pa isti termin poslan dvaput (at-least-once slanje) kalendar prepozna kao isti. Kad se rezervacija otkaže, mail otkazivanja ne nosi `.ics` s `METHOD:CANCEL`, pa termin ostaje u kalendaru dok ga korisnik ne obriše. To nisam radio, jer bi tražilo i `ORGANIZER`, a Gmail i Outlook se tu ponašaju različito, što nisam mogao provjeriti.

Prilog nastaje s obavijesti, kao tijelo maila, i čuva se u koloni `ics` samo dok obavijest čeka slanje (`notifications_prilog_dok_ceka`). Da bi poslao prilog, pošiljalac je prešao sa `SimpleMailMessage` na `MimeMessage` i `MimeMessageHelper`. Da te metode postoje u našoj verziji, provjerio sam u `spring-context-support-7.0.10.jar` (`javap`). Ručno u Gmailu i Outlooku nisam probao da li se prilog otvori kao događaj, samo da Mailpit primi ispravan fajl.

## Bez ShedLocka: svaki posao je siguran i na dvije instance

ShedLock pazi da `@Scheduled` posao radi samo na jednoj instanci u isto vrijeme. Na Renderu radi jedna instanca, ali to se može promijeniti. Koliko sam razumio Renderov opis deploya bez prekida, stara i nova instanca kratko rade zajedno. To nisam provjerio. Umjesto da uvedem zavisnost i tabelu `shedlock`, provjerio sam svaki posao šta se desi kad ga pokrenu dvije instance odjednom.

Istek i zatvaranje su po jedan uslovni `UPDATE` (`WHERE status = 'PENDING' AND ...`). Drugi posao čeka red koji je prvi zaključao, a kad prvi završi, Postgres pod READ COMMITTED ponovo provjeri uslov na novoj verziji reda. Rezervacija više nije `PENDING`, pa je drugi preskoči. Test to radi bez sreće u rasporedu niti: prvi posao drži transakciju otvorenu, drugi krene u svojoj niti, test u `pg_stat_activity` sačeka da drugi stvarno čeka na lock, i tek onda pusti prvog. Drugi tada vrati 0, a svaka rezervacija ima jedan događaj `EXPIRED` i jednu obavijest. Iznenadilo me šta se desi kad se lista izabere podupitom (`WHERE id IN (SELECT ... WHERE status = 'PENDING')`): drugi posao istekne iste rezervacije još jednom. Postgres ponovo provjerava samo uslov na samom redu, a podupit ostaje iz starog snimka.

Podsjetnik čuva unique indeks. Drugi posao ne vidi podsjetnik prvog dok ovaj nije commitan, pa upiše svoj, čeka na indeksu i padne kad prvi završi. Cijeli njegov krug se poništi, a sljedeći krug podsjetnik više ne traži. U testu je trebao `flush` poslije prvog posla, jer Hibernate inače upiše podsjetnik tek pri commitu i drugi nema na šta čekati. Slanje mailova koristi `FOR UPDATE SKIP LOCKED`, pa druga instanca preskoči obavijest koju prva šalje. To je testirano u koraku slanja.

Cijena: kad dvije instance stvarno rade, istek i podsjetnici rade isti upit dvaput, a kod podsjetnika jedan krug ponekad padne s greškom u logu. Za jedan posao u minuti to je u redu. ShedLock bih uveo kad posao postane skup, npr. statistika preko svih organizacija.

Uz ovo sam dodao dva testa za kriterij Faze 5 koji su do sada bili pokriveni samo posredno. Prvi šalje potvrdu dok je Mailpit pauziran (`docker pause`). Rezervacija nastane, prvi pokušaj padne s `Exception reading response` poslije timeouta od 5 sekundi, a poslije pomjeranja `next_attempt_at` drugi pokušaj stigne u Mailpit (`SENT`, 2 pokušaja). Drugi test provjerava da isti link za novu lozinku drugi put vraća `409`.

## Statistika: period po početku termina, agregati u bazi, popunjenost iz kalendara

Period se gleda po početku termina, ne po trenutku kad je rezervacija napravljena. "Novembar" je ono što se u novembru dešavalo u čitaonici, i to vlasnik sale najčešće pita. Granica je `[od, do)` kao kod termina, pa se dva susjedna mjeseca ne preklapaju. Termin koji počne 31.10. u 23:30 i završi u novembru pripada oktobru.

Brojanje radi baza (`GROUP BY`), a rezultat ide pravo u Java record. Za JPQL je to `select new ...`, a za native upite Spring Data JPA 4.1.2 sam pozove konstruktor recorda po redoslijedu kolona. To sam prvo vidio `javap`-om na `spring-data-jpa-4.1.2.jar` (`isDtoProjection`, `instantiateDto`), a onda u testu. Redoslijed kolona je zato dio ugovora s recordom, a test ga provjerava.

Provider vidi samo resurse koje vodi. Za to su posebne metode (`...ForProvider`), a ne jedan `providerId` koji smije biti `null` i tada znači "sve". Greška u servisu tako može dati premalo podataka, ali ne i tuđe. Organizacija ostaje u uslovu i kod providera, jer isti korisnik može voditi resurse u dvije organizacije. Za svaki upit postoji test u kojem Selma vodi i salu u drugoj organizaciji, a mutacija bez uslova po organizaciji ga obori.

Dan i sat za "najtraženije termine" se računaju s `AT TIME ZONE` i zonom organizacije iz tabele `organizations`. Bez toga `extract` radi u zoni konekcije, pa bi srijeda u 9:00 prije i poslije 28.10.2035. pala u 7 i 8 po UTC-u. Test s te dvije srijede to pokazuje. Traženost broji sve statuse, i otkazane, jer je i taj termin neko htio.

Popunjenost je zauzete minute kroz ponuđene minute. Zauzeto je `CONFIRMED`, `COMPLETED` i `NO_SHOW`. Nedolazak ulazi jer taj termin niko drugi nije mogao uzeti. Ponuđene minute računa isti `SlotCalculator` koji pravi kalendar, a ne poseban SQL nad pravilima. Tako postoji jedna definicija radnog vremena, s već testiranim DST-om i izuzecima. Broje se termini, ne radno vrijeme: 08:00-10:30 sa satnim terminima daje 120 minuta. Kalkulator je zato dobio `allBetween`, koji vraća i prošle termine, a stari `between` ih samo filtrira. Cijena su 2-3 upita po resursu, što ću izmjeriti s većim brojem resursa.

Ograničenje: radno vrijeme i deaktivacija resursa nemaju historiju. Ako admin sredinom mjeseca promijeni radno vrijeme, popunjenost za prvu polovinu se računa po novom. Deaktiviran resurs se i dalje računa po svojim pravilima, jer je u prošlom periodu možda radio, a inače bi imao zauzete minute bez ijedne ponuđene. Popunjenost zato može izaći i preko 100 %, npr. kad se radno vrijeme skrati poslije rezervacija. Ne sječem je na 100 %. Plan je da API vrati obje brojke, a ne samo procenat.

## Frontend kostur: Vite, React i JavaScript, fontovi kroz CSS

Frontend je React 19.3 s Viteom 8.3, u običnom JavaScriptu. Kostur je prvo bio napravljen sa šablonom `react-ts`, ali TypeScript ne znam, a ne želim u projektu kod koji ne mogu objasniti liniju po liniju. Zato sam ga isti dan prebacio na šablon `react` (`create-vite` 9.3.4): bez `tsconfig` fajlova i bez paketa `typescript`. Cijena je da build ne javi kad se polje u DTO-u preimenuje, pa to moraju uhvatiti testovi API sloja (Vitest). `@types/react` je ostao jer ga šablon za JavaScript ima, samo da editor zna dopunjavati React funkcije. Šablon je donio Oxlint umjesto ESLinta i to sam ostavio, jer ima pravila za hookove. Slike i stilove iz šablona sam obrisao.

Fontovi se uvoze iz `index.css` sa `@import`, a ne iz `main.jsx`. Dok je kostur bio u TypeScriptu, uvoz iz koda je pao na dva mjesta. Paket `@fontsource-variable/big-shoulders-display` nema TypeScript deklaracije, pa `tsc` javlja `TS2882: Cannot find module or type declarations for side-effect import`. Kad sam umjesto toga uvezao `.../wght.css`, pao je Vite (`Rolldown failed to resolve import`), jer `exports` u tom paketu dodaje `.css` sam (`"./*": "./*.css"`) i traži `wght.css.css`. Drugi problem sam napravio sam, zaobilazeći prvi. U JavaScriptu bi običan uvoz paketa iz `main.jsx` trebao raditi, ali to nisam probao. `@import` sam ostavio jer su fontovi tako na istom mjestu gdje će biti i tokeni.

Uvozim cijele Fontsource CSS fajlove, sa svim subsetovima. Svaki subset ima svoj `unicode-range`, pa browser skine samo onaj koji tekst stvarno treba. Za naš tekst su to `latin` i `latin-ext`, a ćirilica i vijetnamski ostanu na serveru. To sam vidio u spisku fajlova poslije builda, a nisam mjerio šta browser stvarno skine.

## Stilovi: obične CSS varijable, bez Tailwinda

Vrijednosti iz `docs/DIZAJN.md` su CSS varijable u jednom fajlu, `frontend/src/tokeni.css`. Tailwind nisam uzeo. Njegova podrazumijevana paleta i razmaci bi se morali isključiti da ne uđe nešto čega nema u `DIZAJN.md`, a klase u JSX-u bih morao učiti kao još jedan jezik. Za pet boja i sedam razmaka obične varijable su dovoljne. Nijedna boja se ne piše direktno van `tokeni.css`, i to provjerava test: kad sam žutu u tokenima promijenio za jednu cifru, dodao boju koje u `DIZAJN.md` nema, ili napisao `#ffffff` i `rgb(0 0 0)` u `index.css`, test je svaki put pao.

Vitest je 5.1.0, jer podržava Vite 8 (`peerDependencies` kaže `^6.4.0 || ^7.0.0 || ^8.0.0`). `npm test` ga pokreće jednom, bez praćenja izmjena.

## API sloj u frontendu: jedan fetch, jedna vrsta greške

Frontend zove backend samo kroz `apiFetch` u `frontend/src/api/apiFetch.js`. Svaki odgovor koji nije `2xx` postaje `ApiError` sa dva polja: `status` iz HTTP odgovora i `problem`, tj. Problem Details iz tijela ili `null`. Status se namjerno ne čita iz tijela. U Fazi 4 je `setProperty("status", ...)` pregazio broj sa `"CANCELLED"`, i za to sada postoji test na frontendu. Kad odgovora uopšte nema, `status` je 0. Lokalno se to skoro ne dešava, jer Vite proxy, kad backend ne radi, vrati `502 Bad Gateway` s običnim tekstom. To sam provjerio s ugašenim lažnim backendom na portu 8080.

Bez TypeScripta build ne zna kako izgleda odgovor. Zato funkcija za endpoint odmah provjeri polja bez kojih ekran ne može (`provjeriPolja`) i baci grešku sa putanjom i imenom polja, npr. `/api/status: nema polja vrijeme`. Testovi API sloja koriste JSON prepisan iz backend testova. Ako backend preimenuje polje, ti testovi to ne vide, jer imaju svoju kopiju JSON-a. Ali provjera polja padne odmah u browseru, sa jasnom porukom, a ne kao `undefined` na ekranu.

U dev modu Vite prosljeđuje `/api` na `http://localhost:8080`, pa su frontend i backend za browser isti sajt, i refresh kolačić sa `SameSite=Strict` može raditi. Proxy sam provjerio lažnim backendom koji vraća putanju koju je dobio: stiglo je `/api/status`, bez izmjene. Sa pravim backendom nisam provjerio, jer ga za to moram pokrenuti sa `.env`. Kako se poziva backend u produkciji odlučuje se u Fazi 12, zajedno s domenom.

Odluku TanStack Query ili vlastiti hook sam pomjerio u korak 5, gdje prvi ekran stvarno učitava podatke. `apiFetch` ne zavisi od nje.

## Sesija u frontendu: access token u memoriji, jedan refresh za sve

Access token stoji samo u varijabli u `apiFetch.js`, ne u `localStorage`. Tamo bi ga XSS mogao pročitati. Cijena je da ga poslije osvježavanja stranice nema, pa aplikacija na početku pozove `/api/auth/refresh`. Refresh kolačić je `HttpOnly` i JavaScript ga ne vidi, pa se tako sesija vrati bez nove prijave.

Kad zahtjev s tokenom dobije `401`, `apiFetch` obnovi token i ponovi zahtjev jednom. Više zahtjeva koji u isto vrijeme dobiju `401` čekaju isti promise, pa ide samo jedan refresh. Drugi refresh sa već iskorištenim kolačićem backend vidi kao krađu i poništi cijelu porodicu tokena (odluka iz Faze 2). Ako `401` stigne za zahtjev koji je poslan sa starim tokenom, a token je u međuvremenu već obnovljen, zahtjev se samo ponovi, bez novog refresha. Refresh se ne pokušava za `/api/auth/*` (pogrešna lozinka je isto `401`) ni za zahtjev koji nije imao token. Kad refresh vrati `401`, token se briše i aplikacija prelazi na prijavu. Kad server ne odgovara, sesija se ne prekida, nego zahtjev javi grešku.

Testovi su u `sesija.test.js`. Kad sam privremeno isključio zajednički refresh, test s tri paralelna `401` je pao, jer su otišla tri refresha. Pala su i ostala četiri pravila kad sam ih isključio jedno po jedno. Jedan test je prvo bio slab: provjeravao je zadnji poziv, a to je s greškom bio refresh, ne zahtjev, pa mutacija nije pala. Ispravio sam ga da gleda zadnji poziv na `/api/me`.

Odjava briše token tek kad backend potvrdi. Prvo sam ga brisao i kad poziv ne uspije, ali refresh kolačić briše samo server. Poslije osvježavanja stranice sesija bi se vratila, pa bi to bila lažna odjava. Sada korisnik vidi "Odjava nije uspjela" i ostaje prijavljen.

Na početku aplikacije `SesijaProvider` pozove `obnoviSesiju`. React u dev modu (`StrictMode`) pokrene efekat dvaput, a oba poziva dobiju isti refresh, pa backend ne vidi dva refresha istim kolačićem. Bez zajedničkog refresha bi se korisnik u dev modu odjavljivao na svako osvježavanje.

Forme imaju `noValidate`, pa browser ne provjerava polja sam. Provjeru radi backend, a njegove poruke (`greske` iz `400`) stoje ispod polja. Tako su sve poruke na bosanskom i na jednom mjestu. Ugrađene poruke browsera bi bile na jeziku sistema. Cijena je jedan zahtjev više kad je polje prazno.

Zajednički promise radi samo unutar jednog taba, jer tabovi ne dijele memoriju, ali dijele kolačić. Dva taba koja u istoj sekundi dobiju `401` bi poslala isti kolačić dvaput i backend bi prekinuo sesiju. Zato refresh ide kroz bravu iz Web Locks API-ja (`navigator.locks.request('slotbook-refresh', ...)`), koja u isto vrijeme pušta samo jedan refresh kroz sve tabove istog sajta. Drugi tab čeka i pošalje već novi kolačić, jer browser upiše `Set-Cookie` prije nego što prvi refresh završi i pusti bravu. Svaki tab dobije svoj access token, a oba važe. Test `dvaTaba.test.js` pravi dvije kopije modula, kao dva taba, i koristi pravi `navigator.locks` iz Node 26. Lažni backend odbija stari kolačić kao `RefreshTokenService`. Bez brave je test vratio `[true, false]`, tj. drugi tab je odjavljen, a s bravom `[true, true]`. Web Locks imaju svi današnji browseri. Gdje ih nema, refresh radi kao prije, zaštićen samo unutar taba.

## Moje organizacije: poseban endpoint, bez stranica

Frontend poslije prijave mora znati u kojim je organizacijama korisnik i s kojom ulogom, da zna kuda ga vodi i koje ekrane nudi. Takvog endpointa nije bilo: `GET /api/me` vraća samo korisnika, a `GET /api/organizations/{slug}` traži da slug već znaš. Dodao sam `GET /api/me/organizations`, koji vraća listu `{ id, name, slug, timezone, role }` po nazivu. Mogao sam proširiti `/api/me`, ali onda bi svaki poziv za korisnika vukao i organizacije, a oblik `/api/me` bi se promijenio.

Lista nema stranica, iako ostale liste imaju `PageResponse`. Korisnik je član nekoliko organizacija, a frontendu treba cijela lista odjednom. Organizacija dolazi u istom upitu (`join fetch`), jer je u članstvu `LAZY`. U upitu je jedini uslov korisnik iz tokena. Kad sam ga privremeno poništio sa `or 1 = 1`, pala su 3 od 4 testa u `MyOrganizationsApiTest`, među njima onaj gdje Edin ne smije vidjeti Lejlinu čitaonicu. Čuvar u testu izolacije ovu rutu ne hvata, jer gleda samo rute s ID-jem u putanji.

## Rute i učitavanje podataka: React Router i TanStack Query

Za rute sam uzeo React Router (8.5), u običnom načinu s `<BrowserRouter>` i `<Routes>`, bez njegovih loadera. Adrese su `/prijava`, `/registracija`, `/` (moje organizacije) i `/o/{slug}`. Slug je u adresi, a ne ID, jer se link čita i može poslati nekome. Rute za prijavljene čuva `Cuvar`: dok se sesija provjerava ne prikazuje ništa, a odjavljenog šalje na prijavu i pamti adresu, da se poslije prijave vrati tamo. To je samo za korisnika. Podatke čuva backend, i bez čuvara.

Za podatke sa servera sam uzeo TanStack Query (5.107), a ne vlastiti hook. Vlastiti hook bi bio dvadesetak linija za prvi ekran, ali poslije toga treba keš da lista i stranica organizacije ne zovu isti endpoint dvaput, osvježavanje poslije promjene (nova organizacija mora ući u listu) i ponovni pokušaj. To bih pisao sam i griješio u istim stvarima koje je biblioteka već riješila. Cijena je još jedan koncept za naučiti (ključ upita, keš, `invalidateQueries`) i još jedna zavisnost u buildu. Koliko je build zbog nje veći nisam mjerio. Ponovni pokušaj je ograničen na jedan, i samo kad server nije odgovorio (0, 502-504). `403` ili `404` se ponavljanjem ne mijenjaju, a podrazumijevano bi korisnik čekao grešku nekoliko sekundi.

Dvije stvari sam naučio tek na testu. Prvo, keš pripada osobi: odjava i istek sesije ga brišu (`queryClient.clear()`). Inače bi sljedeća osoba za istim računarom vidjela tuđe organizacije dok se njene ne učitaju. Test to provjerava tako da drugoj osobi namjerno zadrži odgovor. Drugo, kad se korisnik sam odjavi, adresa se ne pamti. U pregledu u browseru Adnan se poslije Hanine odjave prijavio i završio na adresi gdje je ona stala. Podatke to nije otkrilo, jer organizacija nije na njegovoj listi, ali ni smisla nije imalo. Prvi pokušaj (`navigate` poslije odjave u zaglavlju) nije radio, jer se `<Navigate>` u čuvaru izvrši u efektu, poslije njega, i ponovo upiše adresu. Sad sesija ima posebno stanje za odjavu na zahtjev korisnika, pa čuvar zna razliku.

Stranica organizacije je traži u mojoj listi, a ne na `GET /api/organizations/{slug}`. Za tuđu ili nepostojeću adresu piše isto ("ne postoji ili nisi njen član"), kao što ni backend tuđoj osobi ne kaže da organizacija postoji.

Forma za novu organizaciju predlaže adresu iz naziva ("Čitaonica PMF" postaje `citaonica-pmf`, đ postaje dj) dok je korisnik sam ne promijeni. Zona je padajuća lista gradova iz `Intl.supportedValuesOf('timeZone')`, bez fiksnih pomaka, jer ih backend odbija. Zauzeta adresa (`409`) za sada stoji kao greška cijele forme, ne ispod polja, jer `409` nema listu `greske`.

Svaka zaštita ima test koji pada kad se ona ukloni. Probao sam 7 mutacija i svaku je uhvatio bar jedan test. Jednu na prvi pokušaj nije: kad sam izbacio osvježavanje liste poslije nove organizacije, test je i dalje prolazio, jer je lažni backend odgovarao odmah. Popravio sam test tako da lista namjerno čeka, i tek onda je pao.

## Kalendar vidi i zauzete termine, ali ne čije su

`DIZAJN.md` traži da kalendar razlikuje slobodno, zauzeto, moje i moje na čekanju, a `/slots` je vraćao samo slobodne. Moje rezervacije su postojale na `/api/bookings/mine`, ali taj endpoint nema filter po resursu ni danu i ide po stranicama, pa bi frontend morao vući sve i filtrirati sam. Zato `/slots` sada ima i listu `taken`: `{ start, end, myStatus }`. `myStatus` je `PENDING` ili `CONFIRMED` kad je rezervacija moja, a `null` kad je tuđa. Drugi vide samo da je termin zauzet, ne ko ga drži ni da li čeka potvrdu. Postojeća polja su ista, pa je promjena kompatibilna.

Zauzet termin ima vrijeme ponuđenog termina, ne rezervacije, da kalendar ostane mreža istih dužina. Ako admin promijeni dužinu termina, jedan termin može dodirivati više rezervacija, i tada prednost ima moja, da je ne prikažem kao tuđu. Ovaj slučaj nisam testirao.

Prvi test za rezervaciju na čekanju je ubacivao `PENDING` red direktno u bazu i pao na constraintu `bookings_pending_ima_rok`, jer rezervacija na čekanju mora imati rok za potvrdu. Greška je bila u testu, pa sada rezervaciju pravi kroz API na stolu s `autoConfirm: false`. Kad sam privremeno pustio da status vidi svako, pao je test u kojem Lamija ne smije vidjeti status Hanine rezervacije.

## Kalendar: jedan dan, sedmica u jednom pozivu, vrijeme kroz Intl

Kalendar je i na desktopu jedan dan kao lista, isto kao na telefonu. Sedmična mreža bi tražila drugi raspored i strelice u dvije dimenzije, a jednodnevni prikaz radi svugdje. Mreža je zapisana u Dodacima. Termini se učitavaju za sedam dana odjednom (`from` je prvi dan, `to` osmi), a traka s danima samo bira šta se prikazuje. Tako klik na drugi dan ne čeka mrežu.

Za vrijeme nisam uzeo biblioteku. `Intl.DateTimeFormat` s opcijom `timeZone` pretvara trenutak iz backenda u sat i dan u zoni organizacije, a dan je string `"2035-11-19"`, kao `LocalDate` u Javi. Dani se dodaju u UTC-u, gdje dan uvijek ima 24 sata, pa prelazak na ljetno vrijeme ne preskoči ni duplira dan. Za to postoje testovi preko 25. marta 2035, kad sat skače s 02:00 na 03:00. "Danas" je dan u zoni organizacije i računa se jednom, kad se kalendar otvori. Testovi zamrznu vrijeme na 00:30 po Sarajevu, kad je u UTC-u još prethodni dan. Mutacija koja "danas" ili dan termina računa po UTC-u tada obori test, a prije tog testa nijedna nije.

Prvo sam nazive dana i mjeseci uzimao iz `Intl.DateTimeFormat('bs')`. Testovi su prolazili, jer Node ima puni ICU. U Edgeu 157 je pisalo "M11 19, Mon": jezik `bs` postoji, ali nema podataka. `hr` daje "studenoga", a `sr-Latn` "ponedeljak". Zato su nazivi sada dva niza u `vrijeme.js`, a sat se formatira s `en-GB`, gdje su samo brojevi. Test glumi browser bez bosanskog (zamijeni `Intl` prije uvoza modula) i pao je dok nazivi nisu prebačeni u nizove. Firefox i Safari nisam provjerio.

Oznaka zone ide samo kad bi sat na uređaju bio drugačiji. Moj browser je u `Europe/Zagreb`, koji ima isto vrijeme kao Sarajevo, pa poređenje po imenu zone ne bi imalo smisla.

Izbor termina (klik ili Enter) za sada samo ispiše koji je termin izabran. Potvrda i slanje rezervacije su korak 7. Strelice gore i dolje idu samo kroz slobodne termine, jer su samo oni dugmad. Zauzet ili tuđ termin se ne može izabrati, a čitač ekrana ga pročita kao tekst reda.

Svaka od 8 mutacija (stanja, provjera polja `taken`, danas i dan po UTC-u, strelice, odlazak u prošlost, 12-satni format, dodavanje dana kao 24 sata u lokalnoj zoni) oborila je bar jedan test.

## Rezervacija iz kalendara: ključ po namjeri, najbliži slobodan na frontendu

`Idempotency-Key` nastaje kad korisnik izabere termin (`crypto.randomUUID()`), a ne pri kliku na "Rezerviši". Dupli klik i "Pokušaj ponovo" poslije pada mreže zato nose isti ključ, pa backend ne napravi drugu rezervaciju, nego vrati prvu. Kad mreža pukne, ne znamo da li je zahtjev stigao, i poruka to kaže: "Nije jasno da li je rezervacija stigla... ako je stigla, neće se napraviti dvaput." Drugi termin je nova namjera i dobija novi ključ. Dok se zahtjev šalje, dugme je isključeno.

Na `409` backend vraća samo poruku. Najbliži slobodan termin računam na frontendu, iz sedmice koja je već učitana: termin se odmah u kešu označi kao zauzet (`setQueryData`), kalendar se učita ponovo, a ponuda se računa iz onoga što je u kešu. Drugi put je bio polje u odgovoru na `409`, ali to bi značilo računati termine u obradi greške i mijenjati ugovor. Cijena ovog rješenja je da se najbliži traži samo u učitanih 7 dana. Ako ih nema, poruka to kaže. Kod istog razmaka prednost ima kasniji termin, jer je raniji možda već prošao dok korisnik čita poruku.

Potvrda nije modal, nego red ispod termina. Na telefonu se vidi odmah, a fokus i dalje ide gdje treba: na "Rezerviši" kad se potvrda otvori, nazad na termin poslije "Odustani" ili Esc, i na poruku poslije uspjeha, jer dugme nestaje zajedno s potvrdom. Native `<dialog>` bih uzeo za pravi modal, ali jsdom u testovima nema `showModal`, a modal nije ni trebao.

Testovi lažnim backendom koji pamti termine provjeravaju ključ (UUID, isti poslije pada mreže, drugi za drugi termin), jedan zahtjev na dupli klik, termin zauzet odmah poslije `409` dok nova lista namjerno čeka, i fokus. Probao sam 7 mutacija i svaku je uhvatio jedan test. Jednu sam prvi put napisao tako da je pokvarila sintaksu, pa se testovi nisu ni pokrenuli. To se ne računa, i ponovio sam je ispravno.

## Moje rezervacije: rok za otkazivanje stiže s rezervacijom

Plan je tražio da frontend rok za otkazivanje uzme iz odgovora. `MyBookingResponse` ga nije imao, pa bi frontend za rezervaciju poslije roka saznao tek kad klikne "Otkaži" i dobije `409`. Sad ima `cancelDeadline` (početak minus `cancelNoticeHours` resursa) i `organizationName`, jer lista ide preko svih organizacija, pa "Sala 1" bez organizacije ne znači ništa. Pravilo za rok je izdvojeno u `Booking.rokZaOtkazivanje()`, i istu metodu koristi i otkazivanje. Kad sam je privremeno promijenio da vraća samo početak termina, pala su dva testa: novi u `BookingApiTest` i stari `studenticaNeMozeOtkazatiPoslijeRoka`. Polja su nova i ništa staro se nije promijenilo, a oba dolaze iz istog `join fetch` upita.

Backend šalje najkasniji termin prvo. Na ekranu su nadolazeće (`PENDING` ili `CONFIRMED`, a kraj još nije prošao) poredane od najbliže, a prošle i otkazane ostaju kako ih backend šalje. Grupe se računaju samo unutar jedne stranice od 20. Ko ima više od 20 budućih rezervacija, dio će vidjeti tek na stranici "Starije". Za sada mi to nije problem, ali je ograničenje.

Otkazivanje nema `Idempotency-Key`, a ni ne treba ga: drugo otkazivanje iste rezervacije backend odbije sa `409` i `trenutniStatus: CANCELLED`. Ako mreža pukne, pa ponovni pokušaj dobije baš taj odgovor, prvi zahtjev je prošao, i ekran to prikaže kao uspjeh. Ostali `409` (npr. rok je istekao dok je ekran bio otvoren) prikazuju poruku backenda i osvježe listu.

Prvo je pisalo "Otkazati možeš do ponedjeljak, 16. novembar". To nije bosanski, trebalo bi "do ponedjeljka". Sklanjanje naziva dana nije vrijedno koda, pa sada piše "Rok za otkazivanje: ponedjeljak, 16. novembar, 00:00.", isto kao rok za potvrdu u kalendaru.

Probao sam 7 mutacija na frontendu (redoslijed, "već otkazana" kao uspjeh, otkazivanje poslije roka, fokus, osvježavanje liste, otvaranje potvrde, stranice) i svaku je uhvatio bar jedan test.

## Raspored za providera: novi endpoint, po danima, uloga se provjerava i na frontendu

Provider nije imao odakle pročitati ko je rezervisao njegov termin. `/slots` namjerno ne kaže čiji je zauzet termin, a `/bookings/mine` vraća samo moje. Zato postoji `GET /api/resources/{id}/bookings?from&to`, s istim `@PreAuthorize` kao uređivanje resursa (`canManage`: admin, ili provider koji vodi baš taj resurs). Vraća ime klijenta, ne i email, jer provideru ime treba da zna koga čeka. Period je isti kao kod slobodnih termina (do 31 dan, dani u zoni organizacije), pa nema stranica. Ime klijenta stiže kroz jedan `join fetch`, a ne upit po redu. Ruta je ušla u `OrganizationIsolationApiTest`, inače bi taj test pao, jer svaka ruta pod `/api/resources/{id}` mora biti u njemu. Kad sam `canManage` zamijenio sa `canView`, test je pao jer je studentica dobila `200` umjesto `403`.

Na frontendu je raspored jedan dan s "Prethodni dan" i "Sljedeći dan". Za providera je bitno ko dolazi danas, a sedmica s imenima na telefonu ne stane. Link "Raspored" i sama stranica koriste isto pravilo kao `canManage`, napisano još jednom u `vodiResurs.js`. To nije sigurnost, nju čuva backend, nego da klijent ne vidi dugme koje vodi na `403`. Na adresi rasporeda klijent dobije objašnjenje i link na slobodne termine, a raspored se i ne traži.

Potvrda i dolazak idu jednim klikom. Odbijanje i nedolazak se ne mogu vratiti i klijent dobije obavijest, pa traže drugi klik. Nedolazak i dolazak se nude tek kad termin počne, isto kao pravilo u `Booking.prijedi`. `409` (npr. klijent je u međuvremenu otkazao) prikaže poruku backenda u tom redu i osvježi raspored.

Probao sam 7 mutacija na frontendu i svaku je uhvatio bar jedan test.

## Pozivnica: prihvata se klikom, token preživi prijavu

Link iz maila vodi na `/pozivnica#token=...`. Token je iza `#`, pa ga browser ne šalje serveru koji servira frontend i ne ulazi u `Referer`. Pozivnica se ne prihvata sama čim se stranica otvori, nego tek na dugme. Backend nema GET koji bi rekao šta se prihvata, a React u razvoju (StrictMode) pokrene efekt dvaput, pa bi drugi zahtjev dobio `409` "već iskorištena" iako je prvi prošao. Poslije uspjeha ide `navigate(..., { replace: true })`, da token ne ostane u historiji.

Pozvana osoba obično nema nalog. Čuvar ruta je pamtio samo `pathname`, a prijava i registracija nisu jedna drugoj prenosile zapamćenu adresu, pa bi osoba poslije registracije završila na početnoj i bez tokena. Sada se pamti `pathname + hash`, a linkovi između prijave i registracije nose isto stanje. Oba dijela imaju test, i oba su pala kad sam ih vratio na staro.

Greške `403`, `404`, `409` i `410` (drugi email, ne postoji, iskorištena, istekla) skinu dugme i ponude link na organizacije, jer bi ponovni klik dao isti odgovor. Kad server ne odgovara, dugme ostaje.

Lista organizacija se osvježi prije prelaska na novu organizaciju. Test to ne može uhvatiti: link iz maila otvara aplikaciju od nule, keš je prazan i stranica organizacije ionako učita svježu listu. Probao sam usporiti lažni backend, pa je mutacija opet prošla, jer na `/pozivnica` niko ne čita listu organizacija. Ostavio sam osvježavanje za slučaj kad je lista već u kešu. Ispravka iz 10c: tako napisano nije ni tada ništa radilo, vidi "refetchType" ispod.

## Resursi iz frontenda: PUT šalje sve, deaktivacija uz potvrdu

Novi resurs i izmjena koriste istu formu. `PUT` na backendu postavlja cijeli resurs, a `autoConfirm` i `cancelNoticeHours` koji nisu poslani postanu podrazumijevani (automatska potvrda, bez roka). Zato forma šalje sva polja, i ona koja nisu dirana, i to čuva test. Prazno polje za broj ide kao `null`, a ne kao `Number('') = 0`, da backend kaže "Unesi trajanje" umjesto da napravi resurs od 0 minuta.

Deaktivacija se ne može vratiti (vidi gore), pa traži drugi klik, a fokus ide na "Odustani", da Enter ne potvrdi slučajno. Rezervacije koje već postoje se ne otkazuju same, i to piše na ekranu. Osobu koja vodi resurs mijenja samo admin, i bira samo između admina i onih koji vode termine, jer bi za klijenta backend vratio `400`. Lista za izbor je prvih 100 članova, što je i najveća stranica na backendu.

## Radno vrijeme i izuzeci na frontendu

Radno vrijeme je sedmica od ponedjeljka do nedjelje, a svaki dan ima svoje intervale. Šalje se cijela sedmica jednim `PUT`-om, kao što backend i očekuje. Greške stižu po indeksu (`rules[3].end`), pa forma pamti kojim redom je intervale poslala. Za to koristim `variables` iz TanStack mutacije, a ne vlastiti ref, jer se ref ne smije čitati tokom crtanja (Oxlint je to javio). Poruka backenda za preklapanje je pisala "s intervalom rules[0]", a to korisniku ništa ne znači. Sad piše "s drugim intervalom istog dana", a koji je interval pokazuje mjesto greške na ekranu.

Izuzetak se upisuje kao dan i sat u zoni organizacije, a backend traži `OffsetDateTime`. Pomak zavisi od trenutka koji tek računam, pa `uTrenutakSPomakom` proba pomak od dan prije i od dan poslije i zadrži onaj koji se slaže s pomakom u dobijenom trenutku. Kad se ne slaže nijedan, sat tog dana ne postoji (28.3.2027. u 02:30) i forma to kaže prije slanja. Kad se slažu oba, sat postoji dvaput (25.10.2026. u 02:30) i uzimam raniji, da izuzetak radije počne sat ranije. Bez biblioteke, samo `Intl.DateTimeFormat`. Testovi idu preko oba prelaska i preko Nove godine, a 4 mutacije (kasniji od dva, samo jedan pomak, bez provjere, zona uređaja) padaju. Zona uređaja pada samo na testu s New Yorkom, jer je moj računar u istoj zoni kao Sarajevo.

Ugrađena polja za datum i sat (`<input type="date">`, `type="time"`) prikazuju format po jeziku browsera. U headless Edgeu na engleskom piše `08:00 AM` i `03/28/2027`, a vrijednost koju šalju je i dalje `08:00` i `2027-03-28`. Vlastito polje za sat sam stavio u Dodatke.

### refetchType

Poslije novog resursa se ide pravo na njegovo radno vrijeme. Ta stranica traži resurs u listi resursa, pa se lista prvo osvježi. Test koji pamti svaki naslov koji se pojavi je pokazao da se i sa osvježavanjem na tren vidi "Radno vrijeme nije dostupno". Uzrok je u TanStack Query 5.107 (`queryClient.js`): `invalidateQueries` bez `refetchType` ponovo učita samo aktivne upite, a na stranici "Novi resurs" lista nije prikazana. `await` se zato završi odmah, a nova stranica nacrta stari keš. Sa `refetchType: 'all'` se čeka i nova lista. Isto sam ispravio kod prihvatanja pozivnice, gdje sam osvježavanje ostavio baš za slučaj kad je lista u kešu, a baš tada ne bi ništa učitalo.

## Statistika na frontendu

Grafovi su obične HTML tabele, a uz svaki broj stoji traka. Biblioteku za grafove (npr. Recharts) nisam uzeo. Svi grafovi su vodoravne trake jedne boje, a za to je dovoljan `<span>` sa širinom u procentima. Tabela usput daje ono što bi uz SVG graf morao dodatno praviti: čitač ekrana čita red po red, a broj piše i tekstom, pa traka nosi samo sliku (`aria-hidden`). Boje su samo `--tekst` za udio i `--linija` za ostatak, pa `docs/DIZAJN.md` ostaje kakav jeste. Toplotna mapa dan x sat bi bila najbolji prikaz najtraženijih termina, ali traži paletu od svijetle do tamne, a nju bih morao prvo upisati u `DIZAJN.md`. Zato je zasad pet najtraženijih termina kao lista, a mapa je u Dodacima.

Statistika ide po mjesecima, a mjesec je u adresi (`?mjesec=2026-11`), pa se link može poslati nekome. Tekući mjesec se računa u zoni organizacije: 31.12. u 23:30 po UTC-u je u Sarajevu već januar, i to provjerava test.

Ukupna popunjenost je zbir zauzetih minuta kroz zbir ponuđenih, a ne prosjek procenata. Kod prosjeka bi sto koji radi dva sata vrijedio koliko sala koja radi cijeli dan. Procenat se zaokružuje isto kao u `OccupancyCsv`, da ekran i CSV pokažu isti broj.

CSV ne pravim na frontendu, nego ga uzimam s backenda (`Accept: text/csv`), da pravila za Excel (tačka-zarez, BOM, apostrof ispred formule) ostanu na jednom mjestu. `apiFetch` je zato dobio `oblik: 'blob'`. Sa `text()` ne bi radilo: `Body.text()` dekodira UTF-8 i pritom baci BOM, a bez BOM-a Excel ne čita č i ć. Test provjerava da prva tri bajta ostanu `EF BB BF`, a u headless Edgeu sam provjerio i fajl koji se stvarno preuzeo. Taj CSV je došao iz lažnog backenda. Onaj s pravog sam provjerio u Fazi 6, ali preuzimanje s pravog backenda nisam probao.


## Playwright: pravi backend i svoja baza

Glavni tok (prijava, termin, rezervacija, otkazivanje, konflikt) ide kroz pravi backend i pravu bazu, a ne kroz lažni backend iz Vitest testova. Vitest već provjerava kako ekran reaguje na svaki odgovor. Ovaj test treba pokazati da frontend i backend stvarno govore istim jezikom: imena polja, refresh kolačić kroz Vite proxy, `409` koji dolazi iz `EXCLUDE` constrainta, a ne iz mog fixturea. Do ovog koraka je svaki ekran Faze 7 bio provjeren samo s lažnim backendom.

Baza i Mailpit za test su u `frontend/e2e/compose.yml`, s drugim imenom projekta (`slotbook-e2e`) i na drugim portovima (5434, 8026), da test ne dira razvojnu bazu. Baza je u `tmpfs`, pa poslije testa ne ostaje volumen. Playwright pali `webServer` prije `globalSetup`-a (provjerio sam u `playwright/lib/runner/index.js`), pa se baza diže u istoj komandi kao backend, a gasi je `globalTeardown`. Kad backend ne starta, teardown se ne pokrene i kontejneri ostanu upaljeni. To mi se desilo već u prvom pokretanju. Sljedeće pokretanje ih samo preuzme, a test svaki put pravi nove naloge i novu organizaciju, pa stari podaci ne smetaju.

Podatke (admin, organizacija, resurs, radno vrijeme, pozivnica) test pravi kroz API, a kroz browser ide samo ono što se testira. Token pozivnice backend šalje samo mailom, pa ga test čita iz Mailpita, i to čekanje traje do 30 s (`NotificationJob`). Mogao sam člana upisati pravo u bazu, ali onda test zavisi od šeme i zaobilazi pozivnicu.

Radno vrijeme je svaki dan osim današnjeg. Kalendar se otvori na prazan dan i ponudi prvi slobodan, a to je sutra od 08:00, pa test ne zavisi od toga u koliko sati se pokrene. Browser je u zoni `America/New_York`, a sati na ekranu moraju biti sarajevski.

`JWT_SECRET` za backend iz testa se pravi nasumično pri svakom pokretanju. Prvo sam upisao smislenu rečenicu kao vrijednost, a lokalni gitleaks ju je prijavio kao `generic-api-key` (entropija 3,76), isto kao `.env.example` u Fazi 1. Browser je Edge koji je već instaliran (`channel: 'msedge'`), pa Playwright ne skida svoj Chromium. Test zasad nije u CI-ju.

## Pristup po ulogama: ista lista zahtjeva za svaku ulogu

Test izolacije iz Faze 2 je slao 25 zahtjeva samo s tokenom admina druge organizacije. To hvata zaboravljen `@PreAuthorize` i provjeru "admin bilo gdje", ali ne i grešku unutar organizacije: klijent koji čita članove ili provider koji uređuje tuđi sto. Takve provjere su postojale po pojedinim `...ApiTest` klasama, ali ne za svaku rutu. Sad ista lista prolazi i s tokenom klijenta čitaonice i s tokenom providera koji vodi drugi sto u istoj čitaonici. Za svaku ulogu u testu piše šta smije (klijent 6 ruta, provider 8), a sve ostalo mora vratiti `403`. Liste sam napisao rukom, a nisam ih izveo iz `@PreAuthorize` izraza, jer test treba reći šta bi trebalo važiti, a ne ponoviti kod. Bez tokena svih 25 ruta vraća `401`.

Čuvar je do sada gledao samo putanje s ID-jem organizacije, resursa ili rezervacije, pa bi ruta poput `/api/sale/{id}` prošla mimo njega. Sad gleda sve `/api` rute. Svaka je ili u listi zahtjeva ili u listi ruta koje nisu podaci jedne organizacije, a uz svaku od tih 15 piše razlog (prijava, `/api/me`, javni profil organizacije, prihvatanje pozivnice).

Dodao sam i pomiješane ID-jeve: Bojan u putanju stavi svoju teretanu i sto čitaonice. `@PreAuthorize` za promjenu providera gleda samo organizaciju iz putanje, a Bojan je u njoj admin, pa provjera prođe. Sto ipak ostaje Almin, jer ga servis traži sa `findByIdAndOrganizationId` i vrati `404`.

Pet mutacija u kodu aplikacije, i svaka obori test koji treba: klijent smije čitati članove, rezervaciju vidi svaki član organizacije, svaki provider uređuje svaki resurs, promjena providera traži resurs bez organizacije iz putanje (tada stiže `500` umjesto `404`, a u logu je `DataIntegrityViolationException` iz `resources_provider_clan`: sto ni tada ne postane Bojanov, jer baza ne pušta providera koji nije član organizacije resursa), i preimenovana ruta `/api/me` (čuvar javi rutu koje nema u listi). Provider koji vodi baš ovaj sto nije posebna uloga u testu. Da on smije upravljati svojim stolom, dokazuju `ResourceApiTest` i `BookingPrelaziApiTest`. CSV statistike ima istu putanju i metodu kao JSON, pa ga čuvar broji kao jednu rutu, a njegov `403` provjerava `StatsApiTest`.

## Rate limiting prijave i registracije: Bucket4j u memoriji, adresa iz CF-Connecting-IP

Prijava i registracija imaju granicu po IP adresi: 20 prijava u minuti i 20 registracija na sat. Granice su široke namjerno. Cijela grupa studenata na fakultetskom Wi-Fi-ju izlazi na internet s jedne adrese, a uska granica bi zaključala čitaonicu u ponedjeljak ujutro. Brute force na jedan nalog sa više adresa ova granica ne zaustavlja. To je posao zaključavanja naloga (korak 4).

Brojanje radi Bucket4j (`bucket4j_jdk17-core` 8.22.0, bez ijedne zavisnosti, pa ga verzija Spring Boota ne dira). Svaka adresa ima bucket s 20 tokena koji se vraćaju jedan po jedan (`refillGreedy`), a svaki pokušaj, i uspješan, troši jedan. Kad tokena nema, stiže `429` s Problem Details i `Retry-After` u sekundama, zaokruženo naviše, jer bi `0` klijenta odmah vratio na isti `429`. Vrijeme Bucket4j-u dajem kroz `Clock` bean (`TimeMeter`), pa unit test pomjeri sat za 20 s i vidi da se vratio tačno jedan pokušaj.

Provjera je `HandlerInterceptor`, a ne servlet filter. Radi poslije Spring Securityja, a prije kontrolera, dakle prije BCrypt-a i `@Valid`, pa i zahtjev s pogrešnim tijelom troši pokušaj. Izuzetak iz interceptora ide kroz `GlobalExceptionHandler` kao svaka druga greška, pa Problem Details ne pišem ručno.

Bucketi su u `ConcurrentHashMap`, jer radi jedna instanca. Sa dvije bi svaka brojala za sebe i granica bi bila duplo veća, a tada treba Redis (već stoji u Dodacima). Mapa bi rasla sa svakom adresom koja se ikad javila, pa je svakih 10 minuta čisti `@Scheduled` posao. Pun bucket je isti kao nov, pa se baca bez gubitka. Caffeine s `expireAfterAccess` bi to uradio sam, ali to je još jedna zavisnost za nekoliko linija koda. Ograničenje: ako čišćenje izbaci bucket baš dok ga drugi zahtjev troši, taj jedan pokušaj se ne izbroji.

Na Renderu je adresa konekcije uvijek proxy, pa bi bez zaglavlja svi korisnici dijelili jedan bucket, i jedan napadač bi zaključao prijavu svima. `X-Forwarded-For` ne koristim. Renderov vodič za PocketBase kaže da Cloudflare taj header samo dopuni, pa lijevi dio bira pozivalac, a `CF-Connecting-IP` prepiše na svakom zahtjevu ([render.com/articles/host-pocketbase-on-render](https://render.com/articles/host-pocketbase-on-render)). Zato `application-prod.properties` ima `slotbook.rate-limit.client-ip-header=CF-Connecting-IP`, a lokalno je prazno i broji se adresa konekcije. Test šalje lažan `X-Forwarded-For` i dobije `429` kao i bez njega. Na Renderu ovo nisam provjerio, oslonio sam se na taj vodič. Provjera poslije deploya: 21 pogrešna prijava u minuti s laptopa daje `429`, a ista prijava s telefona na mobilnim podacima i dalje `401`.

Svi ostali testovi šalju s `127.0.0.1` i zajedno bi potrošili granicu, pa u testovima `application-default.properties` diže granice na 100000. `AuthRateLimitApiTest` ima svoj Spring kontekst s granicama 3 i 2.

Osam mutacija: zaglavlje se ignoriše, `X-Forwarded-For` ima prednost, prijava i registracija dijele bucket, čišćenje baca sve, Bucket4j sam čita sistemski sat, registracija nije pod granicom, nema `Retry-After`, i zaokruživanje naniže. Prvih sedam padaju na pravom testu. Zaokruživanje naniže je prvo prošlo, jer je test čekao 0,5 s, a tada `Math.max(1, ...)` i naniže i naviše daju 1. Test sad čeka i 1,5 s i traži 2. Usput se pokazalo da `toMillis()` čekanje kraće od milisekunde odsiječe na 0, pa se naviše računa iz nanosekundi.

## Granica mailova: brojanje u tabeli obavijesti, po primaocu

Granica iz prethodnog koraka je po IP adresi, a ovdje to ne pomaže. Ko hoće nekome zatrpati inbox
zahtjevima za reset lozinke, može slati s više adresa. Zato se broji po primaocu: najviše 3 maila
iste vrste istom korisniku (ili emailu, za pozivnice) u zadnjih sat vremena.

Brojim mailove koji su već u tabeli `notifications`, a ne u Bucket4j-u. Svaki mail ionako prolazi
kroz outbox, pa je zapis tu, preživi restart i ne treba nova mapa u memoriji. Upit uzme vremena
zadnja tri maila te vrste (`createdForUserSince`, `LIMIT 3` preko `Pageable`), i ako su tri unutar
sata, čeka se dok najstariji od njih ne izađe iz sata. Za to su u V28 dva indeksa, jer je zahtjev
za reset lozinke javan i bez indeksa bi svaki čitao cijelu tabelu.

Odgovor se razlikuje po toku. Reset lozinke preko granice vrati isti `204` i ne pošalje mail, jer
bi `429` otkrio da nalog postoji (nepoznat email nikad ne dobije `429`). Vlasniku to ne smeta, jer
linkovi iz prva tri maila i dalje važe. Novi link za potvrdu traži prijavu, pa tu nema šta otkriti
i vraća `429` s `Retry-After`. Pozivnica isto vraća `429`, a broji se po emailu preko svih
organizacija, jer bi inače neko napravio deset organizacija i iz svake poslao po tri.

Kod pozivnica je prvi test pao iz razloga koji nisam očekivao. Kad nova pozivnica zamijeni staru,
V24 je kaskadom brisao i mail stare pozivnice, pa granica nije vidjela da je mail otišao, i admin
je mogao slati beskonačno. V28 mijenja FK u `ON DELETE SET NULL`, a servis prije zamjene briše
samo mail koji još čeka slanje. Takav mail nije stigao u inbox i ne treba se brojati. Poslan mail
ostaje bez pozivnice i broji se. Constraint `notifications_primalac` sad traži da mail pozivnice
koji čeka ima pozivnicu, pa ako servis zaboravi obrisati takav mail, baza odbije brisanje
pozivnice umjesto da kasnije ode link koji vraća 404.

Ograničenje: provjera i upis nisu zaključani. Deset istovremenih zahtjeva za reset istog naloga
mogu svi vidjeti manje od tri maila i svi proći. `SELECT ... FOR UPDATE` nad korisnikom bi to
zatvorio, ali za sad nisam, jer je posljedica nekoliko mailova više, a ne preuzet nalog. Nisam to
ni izmjerio.

Osam mutacija, sve padaju na pravom testu: reset bez granice, novi link bez granice, čekanje od najnovijeg maila umjesto od najstarijeg, granica 4 umjesto 3, prozor od 2 sata, servis ne briše mail koji čeka, kaskada iz V24 ostaje, i pogrešan naslov greške. Čekanje od najnovijeg bi prošlo kroz prvu verziju testa, jer je test pomjerao sva tri maila zajedno. Sad je star samo mail od registracije. Kad servis ne obriše mail koji čeka, pozivnica vrati `500` s `violates check constraint "notifications_primalac"`, dakle baza stvarno čuva to pravilo.

## Zaključavanje prijave: po emailu iz zahtjeva, u bazi

Granica po IP adresi ne štiti nalog od napadača koji pogađa lozinku s više adresa. Zato se pogrešne
lozinke broje i po emailu: 5 u prozoru od 15 minuta zaključa prijavu za taj email na 15 minuta.
Dok je zaključano, ni tačna lozinka ne pomaže, a odgovor je `429` s `Retry-After` i naslovom
"Prijava privremeno zaključana".

Broji se po emailu iz zahtjeva, a ne po nalogu. Tabela `login_failures` nema strani ključ na
`users`, pa se nepoznat email zaključa potpuno isto kao postojeći. Da se broji samo za postojeće
naloge, peti pokušaj za izmišljen email bi i dalje davao `401`, a za pravi `429`, i tako bi se
saznalo ko ima nalog. Test šalje po 5 pogrešnih za oba i traži isto tijelo odgovora. Provjera
zaključavanja ide prije BCrypt-a, pa je zaključan odgovor brži od običnog, ali jednako brz za
postojeći i nepostojeći email.

Tabela, a ne mapa u memoriji kao za IP granicu, jer zaključavanje treba preživjeti restart, i jer
je za jedan email bitan tačan broj, a ne približna brzina. Prvi upis ide kroz `INSERT ... ON
CONFLICT DO NOTHING`, da dvije istovremene prve greške ne padnu na duplom ključu, a onda se red
čita sa `FOR UPDATE` i mijenja u Javi (`LoginFailure.recordFailure`). Bez zaključavanja reda bi dvije
istovremene greške pročitale isti broj i jedna bi se izgubila. `LoginLockIstovremenoTest` to
provjerava s 4 niti u 15 krugova. Upis je svoja kratka transakcija, red je zaključan samo dok traje
upis, a ne dok traje BCrypt. Uspješna prijava briše red, a posao svaki sat briše redove koji nisu
zaključani i čiji je prozor istekao, inače bi tabela rasla sa svakim izmišljenim emailom.

Ograničenje koje ostaje: ko zna nečiji email, može mu svakih 15 minuta poslati 5 pogrešnih lozinki
i držati ga zaključanim. To je cijena zaključavanja po nalogu. Brojanje po paru email i adresa bi
to spriječilo, ali bi napadač s više adresa opet imao više pokušaja. Reset lozinke
zaključavanje za sada ne skida, pa vlasnik i s novom lozinkom čeka do isteka.

Osam mutacija, sve padaju na pravom testu: bez provjere zaključavanja prije BCrypt-a, nepoznat email se ne broji, uspješna prijava ne briše greške, bez `FOR UPDATE`, bez `ON CONFLICT`, prozor nikad ne ističe, greška dok je zaključano produžava zaključavanje, i čišćenje briše i red u otvorenom prozoru. Bez `FOR UPDATE` je od 4 istovremene greške u prvom krugu izbrojano 2. Bez `ON CONFLICT` je izbrojana samo jedna, a ostale tri niti nisu ništa upisale. Test je pao na broju prije provjere izuzetaka, pa koji je izuzetak bio nisam vidio.

Ispravka 19.11.2026, poslije koraka 4: V28 je unio sudar koji testovi koraka 3 nisu uhvatili. `InviteIstovremenoTest` je pao tek u cijelom pokretanju, sa `DataIntegrityViolationException` umjesto `InviteRaceException`, a sam je prošao tri puta zaredom. Hipotezu sam provjerio privremenim testom s dvije konekcije: prva obriše mailove koji čekaju za otvorenu pozivnicu (nema ih), druga upiše i commita novu pozivnicu s mailom, a onda prva briše pozivnice, vidi tu novu, i `SET NULL` na njenom mailu koji čeka padne na `notifications_primalac`. Uz kaskadu iz V24 ovo se nije moglo desiti. Constraint je uradio svoj posao, nije pustio mail bez pozivnice, ali je admin dobio `500`. Sad su brisanje maila, brisanje pozivnice i upis nove u istom `try`, a i taj constraint daje `InviteRaceException` (`409`), jer znači isto: druga pozivnica za isti email je nastajala u istom trenutku. Mutacija koja to vrati `InviteIstovremenoTest` hvata samo kad se niti poklope, pa to nije pouzdan dokaz, a deterministički test kroz servis nemam.

## Prijava tek poslije potvrde emaila

U Fazi 2 sam zapisao da registracija treba vraćati isti odgovor i kad nalog postoji. Kad sam krenuo to raditi, vidio sam da to samo po sebi ništa ne sakriva. Napadač registruje tuđi email sa svojom lozinkom i odmah se prijavi tom lozinkom. Ako prijava prođe, nalog nije postojao, a ako dobije `401`, postoji s drugom lozinkom. Naš frontend baš tako radi, registracija pa prijava.

Zato se nalog s nepotvrđenim emailom više ne može prijaviti. Dobije isti `401` kao pogrešna lozinka, i to se broji u zaključavanje kao greška, inače bi se izmišljen nalog zaključao drugačije od postojećeg. Ovo mijenja odluku iz Faze 2 da nepotvrđen email ništa ne zabranjuje.

Posljedica je da novi link za potvrdu više ne može tražiti prijavljen korisnik, jer nepotvrđen ne može ni da se prijavi. `POST /api/me/email-verification` je zamijenjen javnim `POST /api/auth/email-verification` s emailom. Uvijek vraća `204`: za nepoznat email, za već potvrđen i preko granice od tri maila na sat se samo ništa ne pošalje. Granica zato više ne vraća `429` za taj tok, jer bi `429` dobio samo email koji ima nalog.

Cijena: 19 test klasa je u pripremi registrovalo korisnika i odmah se prijavljivalo. Sad potvrđuju email direktno u bazi (`update users set email_verified_at = now()`), osim `PasswordResetApiTest`, gdje se potvrđuje samo u testovima kojima treba prijava starom lozinkom, jer jedan test dokazuje da nova lozinka potvrđuje email. Playwright test potvrđuje email preko linka iz Mailpita. Frontend još nema stranicu na koju link vodi, pa registracija kroz frontend do koraka 10 ne vodi do prijave.

Četiri mutacije padaju na pravom testu: nepotvrđen nalog se prijavljuje, link ide i potvrđenom emailu, link bez granice, i endpoint nije javan.

## Registracija s istim odgovorom i vrijeme odgovora

Registracija sad uvijek vraća `202` bez tijela. Ranije je vraćala `201` s korisnikom, ali tijelo s ID-jem ne može izgledati isto za nov i za postojeći nalog, pa ga više nema. Šta se desilo, saznaje samo vlasnik emaila. Za nov email nastaje nalog i ide link za potvrdu. Za postojeći nepotvrđen ide novi link, jer je to vjerovatno isti čovjek koji prvi mail nije našao. Za postojeći potvrđen ide mail da je neko pokušao napraviti nalog s tim emailom, s linkom za prijavu. Postojeći nalog se ne mijenja, lozinka i ime iz drugog pokušaja ne ulaze. Oba maila imaju granicu od tri na sat.

Dvije registracije za isti nov email u istom trenutku obje vide da naloga nema, a `UNIQUE` pusti samo jednu. Druga je ranije dobijala `409`, a sad kontroler i taj `EmailAlreadyUsedException` pretvara u isti `202`. `RegistracijaIstovremenoTest` šalje 6 niti u 10 krugova i traži samo `202` i jedan nalog po krugu. Handler za `409` sam obrisao, jer do njega više ništa ne dolazi.

Grana za postojeći email ne pravi nalog, pa ne bi trošila BCrypt i bila bi brža za njegovo trajanje. Zato i ona pozove `passwordEncoder.encode` nad lozinkom iz zahtjeva, kao `AuthService.login` za nepoznat email. `VrijemeOdgovoraTest` mjeri medijanu od 30 zahtjeva kroz MockMvc, bez mreže, naizmjenično za obje grupe. Dva pokretanja na mom laptopu: registracija 229 ms za nov email i 223 ms za postojeći, pa 107 i 103 ms. Bez lažnog BCrypt-a test pada.

Reset lozinke je dug iz Faze 5 i tu razlika postoji: 12 ms za nepoznat email i 27 ms za postojeći, u drugom pokretanju 4 i 10 ms. Postojeći email upisuje token i mail, a nepoznat samo pročita da naloga nema. Preko interneta je to u šumu jednog zahtjeva, ali s mnogo zahtjeva bi se moglo izdvojiti. Test zato ne tvrdi da razlike nema, samo da nije veća od 50 ms (toliko bi dodalo sinhrono slanje maila ili BCrypt). Popravak bi bio da se posao za postojeći email radi poslije odgovora, ili da odgovor nikad ne stigne prije nekog fiksnog vremena. Nisam ga uradio, ostaje otvoreno. Mjerenje kroz pravi HTTP na Renderu nisam radio.

Frontend još radi registraciju pa prijavu (`frontend/src/api/auth.js`). Prijava sad za novi nalog vraća `401`, jer email nije potvrđen, a stranice za potvrdu emaila još nema. To popravlja korak 10.

Četiri mutacije padaju na pravom testu: bez lažnog BCrypt-a, istovremena registracija daje grešku, mail "nalog postoji" bez granice, i nepotvrđen nalog dobija mail o nalogu umjesto novog linka.

## Sigurnosna zaglavlja i CORS

Spring Security sam šalje `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` i `Cache-Control: no-cache`. Dodao sam `Referrer-Policy: no-referrer` i, samo za `/api/**`, `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`. API vraća JSON, CSV i slike i ne treba ništa učitavati, pa je najstroža politika i najjednostavnija. Swagger UI učitava skripte i stilove i s tim CSP-om ne radi, zato ga nema van `/api`. Ovo su zaglavlja backenda. Stranice frontenda servira host frontenda, i njihov CSP (skripte, fontovi, `connect-src` prema backendu) ide s deployom u Fazi 12.

HSTS Spring po defaultu šalje samo kad je zahtjev HTTPS. Render završava HTTPS ispred aplikacije i prosljeđuje običan HTTP, a Spring Boot Render ne prepoznaje kao platformu iza proxyja, pa bi `isSecure()` uvijek bio `false` i HSTS se ne bi slao nikad. Druga opcija je bila `server.forward-headers-strategy`, ali to mijenja i adresu klijenta i URL-ove u aplikaciji, a granica pokušaja iz koraka 2 ionako čita `CF-Connecting-IP`. Zato se HSTS šalje uvijek. Browser ga preko običnog HTTP-a ignoriše, pa lokalno ne smeta. Na Renderu nisam provjerio da stiže, provjera je `curl -I https://<backend>/api/status` poslije deploya.

CORS dozvoljava samo origin iz `slotbook.app-url`, iste adrese na koju vode linkovi iz mailova, pa je adresa frontenda na jednom mjestu. Uz kolačić (`allowCredentials`) browser ionako ne prihvata `*`. Dozvoljena zaglavlja su `Authorization`, `Content-Type` i `Idempotency-Key`, a `Retry-After`, `Content-Disposition` i `Location` su izložena, jer ih JavaScript s drugog origina inače ne vidi. Preflight (`OPTIONS`) nema token, pa ga CORS filter obradi prije provjere prijave. Bez `.cors(...)` je preflight dobijao `401`. Tuđi origin dobije `403` bez `Access-Control-Allow-Origin`.

Jedna stvar ostaje za Fazu 12. Refresh kolačić je `SameSite=Strict`, pa ga browser uopšte ne šalje kad su frontend (npr. na Vercelu) i backend (na Renderu) različiti sajtovi, ni s ispravnim CORS-om. Lokalno to rješava Vite proxy. Na produkciji će trebati isto: frontend prosljeđuje `/api` na backend, pa za browser ostaje isti sajt. Tada CORS za glavni tok ni ne treba, ali ostaje ispravno podešen ako se frontend ikad poziva direktno.

Šest mutacija, sve padaju na pravom testu: bez `Referrer-Policy`, HSTS samo na HTTPS, CSP i na Swaggeru, bez CORS-a, svaki origin dozvoljen, i bez izloženih zaglavlja.

## Dnevnik osjetljivih radnji (audit log)

U dnevnik ide ono od čega zavisi ko šta smije u organizaciji: nova organizacija (osnivač postaje admin), poslana pozivnica, prihvaćena pozivnica (neko postaje član s ulogom), i nov, izmijenjen, prebačen na drugog providera ili deaktiviran resurs. Promjene uloge postojećeg člana i uklanjanja člana nema, jer tih radnji još nema u aplikaciji. Brisanje naloga dolazi s korakom 9. Radno vrijeme, izuzeci i slika resursa se ne upisuju. Rezervacije imaju svoju historiju u `booking_events`.

Zapis se piše u istoj transakciji kao radnja. `AuditLogService.record` ima `@Transactional(propagation = MANDATORY)`, pa poziv van transakcije baci `IllegalTransactionStateException` umjesto da tiho otvori svoju. Ako radnja padne poslije zapisa, rollback vrati i zapis, pa u dnevniku nema radnje koja se nije desila. Obrnuto isto važi: radnja ne može proći bez zapisa, osim ako se poziv zaboravi, a to hvata test. Druga opcija je bila događaj (`ApplicationEvent`) i listener, ali onda zapis zavisi od faze listenera, a direktan poziv je jedna linija koju vidim u servisu.

Plan je imao `entity_type`, `entity_id` i `details JSONB`. Uzeo sam jednostavnije: `target_id` bez stranog ključa (zapis ostaje i kad se pozivnica zamijeni novom) i `detail` kao kratak tekst, jer se iz `action` već vidi šta je meta. JSONB bi imao smisla kad bih čuvao staru i novu vrijednost svakog polja, a to admin sad ne čita. ID je redni broj (`GENERATED ALWAYS AS IDENTITY`), ne UUID. U testu se više radnji desi u istoj milisekundi, pa sortiranje po `created_at` ne bi dalo pouzdan redoslijed.

Email pozvane osobe ne ide u dnevnik, samo uloga. Ta osoba možda nikad ne napravi nalog, a zapis iz dnevnika se ne briše. Ko je postao član, vidi se iz prihvaćene pozivnice, gdje je izvršilac sam novi član, s imenom. `actor_id` ima `ON DELETE SET NULL`: kad se nalog obriše, zapis o radnji ostaje bez imena.

Čita samo admin te organizacije, najnovije prvo, po stranicama. `@PreAuthorize` odbije tuđeg admina, a upis je uz to ograničen na organizaciju iz putanje. Ruta je u `OrganizationIsolationApiTest`. Taj test sad provjerava i da zahtjev s tuđim tokenom ne ostavi zapis, a za člana bez prava da u dnevniku ostane samo ono što je njegova uloga smjela (provider pravi svoj resurs, klijent ništa).

Dnevnik baza ne čuva od izmjene. Aplikacija nema `UPDATE` ni `DELETE` nad njim, ali `UPDATE` ili `DELETE` iz SQL konzole bi prošao.

Sedam mutacija. Šest padaju na pravom testu: zapis i bez transakcije radnje, prihvatanje pozivnice bez zapisa, upit bez uslova organizacije, najstarije prvo, dnevnik dostupan i provideru, i odgovor bez provjere za obrisan nalog. Sedma je zapis premješten prije provjere novog providera. Ona preživi, i to je bio cilj: provjera baci `400`, rollback odnese i zapis, pa `odbijenaRadnjaNeOstavljaZapis` i dalje vidi isti broj redova.

## BCrypt snage 4 u testovima

Cijela suita je trajala oko 2:50, a mutacije za jedan korak i po 20 minuta. Najviše je trošio BCrypt: skoro svaki test pravi korisnike kroz pravu registraciju i prijavu, a svaki hash snage 10 traje oko 100 ms. Snaga je sad u `slotbook.bcrypt-strength`: 10 u `application.properties`, 4 u testnom `application-default.properties`. Poslije promjene suita traje 2:11, `AuditLogApiTest` 20 s umjesto 74 s, a test izolacije ispod 4 s umjesto 65 s. Ostatak vremena su Maven, Docker i Spring kontekst.

`VrijemeOdgovoraTest` vraća snagu 10, jer mjeri baš BCrypt. Sa 4 bi lažni BCrypt u registraciji bio u šumu, i mutacija koja ga briše ne bi pala. Provjerio sam: sa snagom 10 i dalje pada. `BcryptSnagaTest` čita `application.properties` i pada ako neko spusti i produkciju, jer nijedan drugi test to ne bi primijetio. Testcontainers reuse nisam uključio. Baza bi tada preživjela između pokretanja, a testovi već računaju na dijeljenu bazu samo unutar jednog pokretanja.
