# SlotBook

[![CI](https://github.com/eminahbabovich/SlotBook/actions/workflows/ci.yml/badge.svg)](https://github.com/eminahbabovich/SlotBook/actions/workflows/ci.yml)

Aplikacija za rezervaciju termina na zajedničkim resursima: učionice za učenje, sale za sastanke, konsultacije, teretana. Danas se to najčešće dogovara porukama u grupi ili tabelom, pa se termini preklapaju i niko ne zna ko je šta zauzeo. SlotBook treba da to riješi tako da dva korisnika ne mogu rezervirati isti termin, čak ni kad kliknu u istoj milisekundi. Svaka organizacija ima svoje resurse, osoblje, klijente i svoju vremensku zonu.

Ovo je moj drugi projekat nakon [RoomSplita](https://github.com/eminahbabovich/roomsplit). Backend učim od nule, pa je cilj da svaku liniju mogu objasniti. Učim uz AI mentora (Claude): on objašnjava i predlaže, a ja pokrećem, provjeravam i pitam dok ne razumijem.

## Status

Faze 1 (infrastruktura), 2 (auth i organizacije), 3 (resursi i dostupnost), 4 (rezervacije i konkurentnost), 5 (pozadinski poslovi i email obavijesti), 6 (statistika) i 7 (frontend) su gotove, a Faza 8 (sigurnost) je u izradi. U frontendu su gotovi prijava, registracija, obnova sesije (jedan refresh za više paralelnih zahtjeva), lista organizacija s formom za novu, kalendar slobodnih termina po danima, rezervacija iz kalendara, lista mojih rezervacija s otkazivanjem, raspored za onoga ko vodi termin (potvrdi, odbij, dolazak, nedolazak), za admina lista članova s formom za pozivnicu, stranica na koju vodi link iz pozivnice, upravljanje resursima (novi, izmjena, deaktivacija, ko ga vodi) i sedmično radno vrijeme s izuzecima. Uz to je gotova i statistika po mjesecima (rezervacije po stanju, popunjenost po resursu, najtraženiji termini) s preuzimanjem CSV-a. Glavni tok (prijava, rezervacija, otkazivanje i termin koji neko uzme prije potvrde) pokriva Playwright test koji ide kroz pravi backend i pravu bazu. Ostali ekrani (raspored, admin, statistika) su provjereni samo s lažnim backendom u Vitest testovima i u browseru. Iz Faze 8 su gotovi granica pokušaja prijave i registracije po IP adresi (`429` s `Retry-After`), granica od tri maila na sat istom primaocu za reset lozinke, novi link za potvrdu i pozivnice, zaključavanje prijave na 15 minuta poslije 5 pogrešnih lozinki za isti email, registracija koja ne otkriva da li nalog postoji (uvijek `202`, a prijava tek poslije potvrde emaila), sigurnosna zaglavlja (CSP za `/api`, HSTS, `Referrer-Policy`) i CORS samo za adresu frontenda, dnevnik osjetljivih radnji koji admin čita za svoju organizaciju (`GET /api/organizations/{id}/audit-log`), izvoz svih podataka o korisniku u JSON-u (`GET /api/me/export`), brisanje naloga kao anonimizacija uz potvrdu lozinkom (`DELETE /api/me`), sve to i u frontendu (ekran "pogledaj inbox" poslije registracije, stranica za potvrdu emaila, poruka sa satom sljedećeg pokušaja kod `429`, stranica `/nalog` s izvozom i brisanjem), i test pristupa: svaki endpoint nad podacima organizacije proba se s tokenom admina druge organizacije, klijenta i providera drugog resursa iz iste organizacije, i bez tokena.

U Fazi 1 je urađeno: Spring Boot projekat, PostgreSQL i Mailpit u Docker Composeu, prva Flyway migracija koja uključuje `btree_gist`, endpoint `GET /api/status`, globalno rukovanje greškama u formatu Problem Details (poruke na bosanskom), Swagger UI, CI na GitHub Actions (build, testovi, pokrivenost, skeniranje tajni), `Dockerfile` i profil `prod`. Provjerio sam i da `btree_gist` radi na Neon-u (PostgreSQL 17.11), pa `EXCLUDE` constraint ostaje plan za zaštitu od preklapanja. Backend je deployan na Render (free plan) s bazom na Neon-u i `GET /api/status` odgovara.

U Fazi 2 su urađeni registracija (`POST /api/auth/register`, od Faze 8 uvijek `202` bez tijela), prijava s JWT access tokenom (`POST /api/auth/login`), `GET /api/me` i refresh tokeni u httpOnly kolačiću, s rotacijom i detekcijom ponovne upotrebe (`/api/auth/refresh`, `/api/auth/logout`). Prijavljen korisnik može napraviti organizaciju (`POST /api/organizations`) i tada postaje njen `ADMIN`, a javni profil organizacije se vidi bez prijave (`GET /api/organizations/{slug}`). Admin vidi članove svoje organizacije s ulogama (`GET /api/organizations/{id}/members`), a klijent iste organizacije i admin neke druge dobiju `403`. Uloge se provjeravaju iz baze kod svakog zahtjeva preko `@PreAuthorize`. Admin može pozvati osobu s ulogom (`POST /api/organizations/{id}/invites`), a ona postaje član kad prihvati pozivnicu prijavljena s istim emailom (`POST /api/invites/accept`). Pozivnica stiže emailom, s linkom koji važi 7 dana. Admin token ne vidi. Izolaciju organizacija dokazuje test u kojem admin jedne organizacije na svakom endpointu druge dobije `403` i ništa se ne promijeni. Isti test padne ako se pojavi novi endpoint nad organizacijom koji nije u njemu. Nisam provjerio da li Render već vrti ovu verziju.

U Fazi 3 admin ili provider organizacije može napraviti resurs, npr. salu ili termin konsultacija s trajanjem termina (`POST /api/organizations/{id}/resources`). Provider resursa je onaj ko ga napravi, a baza ne pušta da provider bude neko ko nije član te organizacije. Svaki član organizacije vidi njene resurse po stranicama (`GET /api/organizations/{id}/resources?page=0&size=20`, najviše 100 po stranici), a lista članova od sada isto ide po stranicama. Admin može mijenjati svaki resurs, a provider samo one koje vodi (`PUT /api/organizations/{id}/resources/{resourceId}`). Resurs se ne briše nego deaktivira (`DELETE` na istoj adresi), i tada ga klijent više ne vidi u listi. Admin može resurs prebaciti na drugog providera ili admina iz organizacije (`PUT .../resources/{resourceId}/provider`). Jedan resurs se čita i preko `GET /api/resources/{resourceId}`, gdje se organizacija za provjeru pristupa uzima iz samog resursa. Sedmično radno vrijeme resursa se postavlja odjednom za cijelu sedmicu (`PUT /api/resources/{resourceId}/availability`), u lokalnom vremenu zone organizacije, a intervali istog dana se ne smiju preklapati, što čuva i baza. Izuzeci poput godišnjeg ili praznika se dodaju kao tačan period s vremenskom zonom (`/api/resources/{resourceId}/time-off`), a vide ih samo admin i provider. Slobodni termini se računaju iz radnog vremena i izuzetaka za period do 31 dan (`GET /api/resources/{resourceId}/slots?from=2035-03-19&to=2035-03-27`), u zoni organizacije i ispravno preko prelaska na ljetno vrijeme. Od Faze 4 se od njih oduzimaju zauzeti termini, a od Faze 7 isti odgovor ima i listu zauzetih (`taken`), gdje korisnik vidi koji su od njih njegovi i da li čekaju potvrdu. Admin ili provider resursa može postaviti sliku (JPEG, PNG ili WebP do 2 MB, `POST /api/resources/{resourceId}/image`), a čita je svako ko vidi resurs. Slike se čuvaju u folderu na disku ili u S3-kompatibilnom skladištu (`STORAGE_TYPE=s3`). Na Renderu je za sada disk, pa slike ne preživljavaju deploy dok se ne podesi Cloudflare R2.

U Fazi 4 član organizacije rezerviše termin koji kalendar nudi (`POST /api/resources/{resourceId}/bookings`), a šalje samo početak, jer kraj određuje trajanje termina na resursu. Dvije aktivne rezervacije istog resursa se ne mogu preklopiti, i to čuva baza (`EXCLUDE` constraint). Test šalje 20 istovremenih zahtjeva za isti termin kroz pravi HTTP server, u četiri runde, i u svakoj prođe tačno jedan, a ostalih 19 dobije `409`. Na putu do toga sam naletio na deadlock u Postgresu, opisan u [docs/ODLUKE.md](docs/ODLUKE.md). Ponovljen zahtjev s istim `Idempotency-Key` vraća istu rezervaciju, a isti ključ za drugi termin daje `422`. Resurs je na automatskoj ili ručnoj potvrdi. Admin ili provider potvrđuje, odbija i označava dolazak ili nedolazak, a klijent može otkazati svoju rezervaciju do roka koji piše na resursu (`POST /api/bookings/{id}/confirm`, `/reject`, `/cancel`, `/check-in`, `/no-show`). Nedozvoljen prelaz, npr. potvrda otkazane rezervacije, vraća `409`. Svaka promjena statusa ide u historiju (`GET /api/bookings/{id}/events`), a korisnik vidi svoje rezervacije iz svih organizacija (`GET /api/bookings/mine`), od Faze 7 s nazivom organizacije i rokom do kada ih može sam otkazati. Admin i provider resursa vide raspored s imenima klijenata (`GET /api/resources/{resourceId}/bookings?from&to`), a klijent dobije `403`. Slobodni termini više ne nude zauzete. Nisam provjerio da li Render već vrti ovu verziju.

U Fazi 5 rezervacija na ručnoj potvrdi čeka providera 24 sata, a najkasnije do početka termina. Pozadinski posao svake minute prebaci isteklu u `EXPIRED` i oslobodi termin, a potvrđenu zatvori (`COMPLETED`) dan poslije kraja termina, da provider stigne označiti nedolazak. Isti posao klijentu pošalje podsjetnik dan prije potvrđenog termina, ako je rezervacija napravljena bar dan unaprijed. Ako posao kasni, nova rezervacija na istom resursu sama oslobodi istekle. Svaka promjena koju neko treba saznati (nova rezervacija na čekanju, potvrda, odbijanje, otkazivanje, istek) upisuje obavijest u tabelu `notifications`, u istoj transakciji kao promjena. Poseban posao svakih 30 sekundi šalje te obavijesti emailom. Ako SMTP ne odgovara, pokušava ponovo poslije 1, 5, 25 i 125 minuta, a poslije petog neuspjeha odustaje i zapiše razlog. Potvrda rezervacije nosi prilog `termin.ics`, koji se otvori u kalendaru. Test šalje potvrdu kroz pravi SMTP u Mailpit i provjerava naslov, tekst sa č, vrijeme u zoni organizacije i prilog. Lokalno se mailovi vide u Mailpitu na http://localhost:8025. Na Renderu SMTP još nije podešen, pa tamo obavijesti padaju i poslije pet pokušaja ostaju `FAILED`. To dolazi u Fazi 12.

Pozadinski poslovi nemaju ShedLock, jer su sigurni i kad rade na dvije instance u isto vrijeme. Test pokrene dva posla isteka tako da drugi stvarno čeka na lock prvog, i svaka rezervacija istekne jednom. Kod podsjetnika drugi padne na unique indeksu, pa ostane jedan podsjetnik. Kod slanja drugi preskoči obavijest koju prvi šalje (`SKIP LOCKED`). Test šalje i potvrdu dok je Mailpit pauziran: rezervacija nastane, prvi pokušaj padne na timeoutu, a drugi pokušaj stigne u Mailpit.

Poslije registracije stiže mail s linkom za potvrdu emaila, koji važi 24 sata i može se iskoristiti jednom (`POST /api/auth/verify-email`, token u tijelu). Novi link se traži bez prijave (`POST /api/auth/email-verification` s emailom, uvijek `204`), a `GET /api/me` kaže da li je email potvrđen. `GET /api/me/organizations` vraća organizacije prijavljenog korisnika s njegovom ulogom u svakoj (od Faze 7). Baza čuva samo hash tokena, a tijelo maila se briše čim je poslan. Od Faze 8 se nalog s nepotvrđenim emailom ne može prijaviti, i dobije isti `401` kao pogrešna lozinka. Link vodi na stranicu frontenda (`APP_URL`), koja još ne postoji, pa registracija kroz frontend do koraka 10 Faze 8 ne vodi do prijave.

Ko zaboravi lozinku, traži link na `POST /api/auth/password-reset`. Odgovor je isti i za email bez naloga, da se ne može provjeriti ko je registrovan. Link važi sat vremena. Nova lozinka se postavlja na `POST /api/auth/password-reset/confirm`, a time prestaju važiti stariji linkovi za reset i sve sesije na svim uređajima.

U Fazi 6 admin i provider vide statistiku organizacije za period do godinu dana (`GET /api/organizations/{id}/stats?from=2035-11-01&to=2035-12-01`, datumi u zoni organizacije, `to` se ne uključuje). U njoj je broj rezervacija po statusu, najtraženiji termini po danu u sedmici i satu, i popunjenost svakog resursa kao zauzete i ponuđene minute. Admin vidi cijelu organizaciju, provider samo resurse koje vodi, a klijent dobije `403`. Period se gleda po početku termina, a dan i sat po lokalnom vremenu, pa srijeda u 9 ostaje srijeda u 9 i preko prelaska na zimsko vrijeme. Ponuđene minute računa isti kod koji pravi kalendar slobodnih termina. Sa zaglavljem `Accept: text/csv` isti endpoint vraća popunjenost kao CSV za Excel. Kriterij faze dokazuje test koji upiše mjesec rezervacija u čitaonici i poredi cijeli JSON s brojkama koje sam izračunao ručno. Indeks za statistiku sam izmjerio na 408 hiljada rezervacija: za jedan mjesec upit je pao s oko 20 na oko 2 ms, a za cijelu godinu se ništa ne mijenja. Brojke su u [docs/PERFORMANSE.md](docs/PERFORMANSE.md). Radno vrijeme nema historiju, pa se prošli period računa po sadašnjem radnom vremenu. Nisam provjerio da li Render već vrti ovu verziju.

| Faza | Šta | Status |
|---|---|---|
| 1 | Temelji i infrastruktura | gotovo |
| 2 | Auth i organizacije | gotovo |
| 3 | Resursi i dostupnost, slobodni termini | gotovo |
| 4 | Rezervacije i konkurentnost | gotovo |
| 5 | Pozadinski poslovi i email obavijesti | gotovo |
| 6 | Statistika | gotovo |
| 7 | Frontend (jezgro) | gotovo |
| 8 | Sigurnost i GDPR | u izradi |
| 9 | Observability i performanse | nije počelo |
| 10 | Napredne funkcije (lista čekanja, iCal, SSE...) | nije počelo |
| 11 | Plaćanje (Stripe, test mode) | nije počelo |
| 12 | Deploy i prezentacija | nije počelo |
| 13 | Završni pregled i dodaci | nije počelo |

Detaljan plan, model podataka i skica API-ja su u [PROJECT_PLAN.md](PROJECT_PLAN.md), odluke koje sam donio i zašto u [docs/ODLUKE.md](docs/ODLUKE.md), slojevi i model podataka u [docs/ARHITEKTURA.md](docs/ARHITEKTURA.md), a mjerenja u [docs/PERFORMANSE.md](docs/PERFORMANSE.md).

Backend ima 404 testa i svi prolaze (zadnje pokretanje 19.11.2026.). Frontend ima 188 Vitest testova i 2 Playwright testa (zadnje pokretanje 21.11.2026.). JaCoCo je mjerio 97,1 % linija glavnog koda (1385 od 1427) 15.11.2026, iz jednog čistog pokretanja, dok je testova bilo 334. Paket `stats` je pokriven cijeli. Najviše nepokrivenih linija ima `GlobalExceptionHandler` (12): handler za istovremenu izmjenu rezervacije (`@Version`) i handler za dvije istovremene pozivnice, oba provjerena u servisnom testu, ali ne i kroz HTTP, i dio opisa stanja u poruci o nedozvoljenom prelazu. Nepokrivena je i grana u kojoj isti `Idempotency-Key` u istom trenutku stigne za dva različita resursa, i grana u kojoj se mail ne može ni sastaviti (`MessagingException`).

## Tehnologije

Backend je Java 21 sa Spring Bootom 4.1.2 (Hibernate 7.4), PostgreSQL 17 i Flywayom za migracije. API se dokumentira springdoc-om 3.2.0. Testovi koriste JUnit i Testcontainers, pa se vrte na pravom PostgreSQL-u u Dockeru, a ne na H2 ili na mojoj lokalnoj bazi. Pokrivenost mjeri JaCoCo, a CI na svaki push pokreće testove i gitleaks nad cijelom git historijom.

Frontend je React 19 s Viteom 8, u JavaScriptu. Fontovi su self-hostani preko Fontsource paketa. Backend radi na Renderu, a baza je na Neon-u. Vercel za frontend i Cloudflare R2 za slike su planirani i još nisu podešeni.

## Pokretanje lokalno

Treba ti Java 21 i Docker. Maven ne treba instalirati, projekat dolazi s `mvnw`.

Prvo napravi `.env` i u njemu promijeni lozinku baze. Ako ti je port 5432 već zauzet (meni jeste, drži ga baza iz RoomSplita), promijeni i `POSTGRES_PORT`. U `JWT_SECRET` ide nasumičan ključ kojim se potpisuju tokeni, najmanje 32 bajta. Bez njega aplikacija ne starta. U PowerShellu se pravi ovako:

```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

```powershell
copy .env.example .env
notepad .env
docker compose up -d
```

Spring ne čita `.env` sam, to radi samo Docker Compose. Zato ga prije starta učitaj u PowerShell prozor, pa pokreni aplikaciju:

```powershell
cd backend
Get-Content ..\.env | ForEach-Object {
    if ($_ -match '^\s*([^#=\s][^=]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1].Trim(), $Matches[2].Trim())
    }
}
.\mvnw.cmd spring-boot:run
```

U IntelliJ-u se isto riješi tako da se `.env` doda u run konfiguraciju (polje Environment variables, ili plugin EnvFile).

Kad aplikacija radi, Swagger UI je na http://localhost:8080/swagger-ui.html, provjera zdravlja na http://localhost:8080/actuator/health, a Mailpit (lažni SMTP server koji hvata emailove) na http://localhost:8025.

Testovi traže da Docker radi, a ne traže `.env`:

```powershell
cd backend
.\mvnw.cmd test
```

Frontend traži Node (koristim 26) i radi na http://localhost:5173. Vite zahtjeve na `/api` prosljeđuje backendu na portu 8080, pa backend treba biti pokrenut. Testovi (Vitest) ne traže backend:

```powershell
cd frontend
npm install
npm run dev
npm test
```

Playwright test glavnog toka sam podigne svoju bazu i Mailpit (`frontend/e2e/compose.yml`, portovi 5434 i 8026), backend i Vite, i na kraju ugasi kontejnere. Traži Docker i Microsoft Edge, a portovi 8080 i 5173 moraju biti slobodni, pa razvojni backend i `npm run dev` prvo ugasi. Traje oko minut i po, najviše zbog starta backenda i čekanja maila s pozivnicom:

```powershell
cd frontend
npm run e2e
```

Za produkciju se uključuje profil `prod` (`SPRING_PROFILES_ACTIVE=prod`). Tada se baza spaja preko varijabli `DB_URL`, `DB_USER` i `DB_PASSWORD`, a port se uzima iz `PORT`. `JWT_SECRET` treba i tamo, i mora biti drugi ključ od lokalnog.

## Struktura

`backend/` je Spring Boot aplikacija, a paketi su po funkcionalnosti (`ba.slotbook.booking`, `ba.slotbook.resource`...), ne po sloju. `frontend/` je React aplikacija (Vite, JavaScript), a Playwright test je u `frontend/e2e/`. Pozivi backenda su samo u `frontend/src/api/`, a boje, fontovi i razmaci samo u `frontend/src/tokeni.css`. U `docs/` su `ODLUKE.md` (odluke i zašto) i `ARHITEKTURA.md` (slojevi, tok zahtjeva i model podataka) i `PERFORMANSE.md` (mjerenja, a SQL skripte za njih su u `docs/mjerenja/`). `docker-compose.yml` diže PostgreSQL i Mailpit za lokalni razvoj. `.github/workflows/ci.yml` je CI, a `.github/dependabot.yml` sedmično traži zastarjele zavisnosti.

## Live demo i screenshotovi

Backend radi na https://slotbook-dxvs.onrender.com. Za sada ima samo [`/api/status`](https://slotbook-dxvs.onrender.com/api/status), [`/actuator/health`](https://slotbook-dxvs.onrender.com/actuator/health) i [Swagger UI](https://slotbook-dxvs.onrender.com/swagger-ui/index.html). Sve ostale adrese traže prijavu i bez nje vraćaju 401, pa i one koje ne postoje.

Pravog demoa još nema: demo računi stižu u Fazi 12, a screenshotovi kad bude frontenda. Free plan na Renderu uspavljuje servis poslije 15 minuta bez saobraćaja, pa prvi zahtjev poslije pauze traje oko minutu.

Repo je za sada privatan, pa CI značka iznad vjerovatno neće biti vidljiva nikome osim meni.
