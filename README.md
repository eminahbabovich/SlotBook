# SlotBook

[![CI](https://github.com/eminahbabovich/SlotBook/actions/workflows/ci.yml/badge.svg)](https://github.com/eminahbabovich/SlotBook/actions/workflows/ci.yml)

Aplikacija za rezervaciju termina na zajedničkim resursima: učionice za učenje, sale za sastanke, konsultacije, teretana. Danas se to najčešće dogovara porukama u grupi ili tabelom, pa se termini preklapaju i niko ne zna ko je šta zauzeo. SlotBook treba da to riješi tako da dva korisnika ne mogu rezervirati isti termin, čak ni kad kliknu u istoj milisekundi. Svaka organizacija ima svoje resurse, osoblje, klijente i svoju vremensku zonu.

Ovo je moj drugi projekat nakon [RoomSplita](https://github.com/eminahbabovich/roomsplit). Backend učim od nule, pa je cilj da svaku liniju mogu objasniti. Učim uz AI mentora (Claude): on objašnjava i predlaže, a ja pokrećem, provjeravam i pitam dok ne razumijem.

## Status

Faze 1 (infrastruktura), 2 (auth i organizacije), 3 (resursi i dostupnost) i 4 (rezervacije i konkurentnost) su gotove. U izradi je Faza 5, pozadinski poslovi i email obavijesti.

U Fazi 1 je urađeno: Spring Boot projekat, PostgreSQL i Mailpit u Docker Composeu, prva Flyway migracija koja uključuje `btree_gist`, endpoint `GET /api/status`, globalno rukovanje greškama u formatu Problem Details (poruke na bosanskom), Swagger UI, CI na GitHub Actions (build, testovi, pokrivenost, skeniranje tajni), `Dockerfile` i profil `prod`. Provjerio sam i da `btree_gist` radi na Neon-u (PostgreSQL 17.11), pa `EXCLUDE` constraint ostaje plan za zaštitu od preklapanja. Backend je deployan na Render (free plan) s bazom na Neon-u i `GET /api/status` odgovara.

U Fazi 2 su urađeni registracija (`POST /api/auth/register`), prijava s JWT access tokenom (`POST /api/auth/login`), `GET /api/me` i refresh tokeni u httpOnly kolačiću, s rotacijom i detekcijom ponovne upotrebe (`/api/auth/refresh`, `/api/auth/logout`). Prijavljen korisnik može napraviti organizaciju (`POST /api/organizations`) i tada postaje njen `ADMIN`, a javni profil organizacije se vidi bez prijave (`GET /api/organizations/{slug}`). Admin vidi članove svoje organizacije s ulogama (`GET /api/organizations/{id}/members`), a klijent iste organizacije i admin neke druge dobiju `403`. Uloge se provjeravaju iz baze kod svakog zahtjeva preko `@PreAuthorize`. Admin može pozvati osobu s ulogom (`POST /api/organizations/{id}/invites`), a ona postaje član kad prihvati pozivnicu prijavljena s istim emailom (`POST /api/invites/accept`). Dok ne dođe slanje emaila u Fazi 5, token pozivnice adminu stiže u odgovoru. Izolaciju organizacija dokazuje test u kojem admin jedne organizacije na svakom endpointu druge dobije `403` i ništa se ne promijeni. Isti test padne ako se pojavi novi endpoint nad organizacijom koji nije u njemu. Nisam provjerio da li Render već vrti ovu verziju.

U Fazi 3 admin ili provider organizacije može napraviti resurs, npr. salu ili termin konsultacija s trajanjem termina (`POST /api/organizations/{id}/resources`). Provider resursa je onaj ko ga napravi, a baza ne pušta da provider bude neko ko nije član te organizacije. Svaki član organizacije vidi njene resurse po stranicama (`GET /api/organizations/{id}/resources?page=0&size=20`, najviše 100 po stranici), a lista članova od sada isto ide po stranicama. Admin može mijenjati svaki resurs, a provider samo one koje vodi (`PUT /api/organizations/{id}/resources/{resourceId}`). Resurs se ne briše nego deaktivira (`DELETE` na istoj adresi), i tada ga klijent više ne vidi u listi. Admin može resurs prebaciti na drugog providera ili admina iz organizacije (`PUT .../resources/{resourceId}/provider`). Jedan resurs se čita i preko `GET /api/resources/{resourceId}`, gdje se organizacija za provjeru pristupa uzima iz samog resursa. Sedmično radno vrijeme resursa se postavlja odjednom za cijelu sedmicu (`PUT /api/resources/{resourceId}/availability`), u lokalnom vremenu zone organizacije, a intervali istog dana se ne smiju preklapati, što čuva i baza. Izuzeci poput godišnjeg ili praznika se dodaju kao tačan period s vremenskom zonom (`/api/resources/{resourceId}/time-off`), a vide ih samo admin i provider. Slobodni termini se računaju iz radnog vremena i izuzetaka za period do 31 dan (`GET /api/resources/{resourceId}/slots?from=2035-03-19&to=2035-03-27`), u zoni organizacije i ispravno preko prelaska na ljetno vrijeme. Od Faze 4 se od njih oduzimaju zauzeti termini. Admin ili provider resursa može postaviti sliku (JPEG, PNG ili WebP do 2 MB, `POST /api/resources/{resourceId}/image`), a čita je svako ko vidi resurs. Slike se čuvaju u folderu na disku ili u S3-kompatibilnom skladištu (`STORAGE_TYPE=s3`). Na Renderu je za sada disk, pa slike ne preživljavaju deploy dok se ne podesi Cloudflare R2.

U Fazi 4 član organizacije rezerviše termin koji kalendar nudi (`POST /api/resources/{resourceId}/bookings`), a šalje samo početak, jer kraj određuje trajanje termina na resursu. Dvije aktivne rezervacije istog resursa se ne mogu preklopiti, i to čuva baza (`EXCLUDE` constraint). Test šalje 20 istovremenih zahtjeva za isti termin kroz pravi HTTP server, u četiri runde, i u svakoj prođe tačno jedan, a ostalih 19 dobije `409`. Na putu do toga sam naletio na deadlock u Postgresu, opisan u [docs/ODLUKE.md](docs/ODLUKE.md). Ponovljen zahtjev s istim `Idempotency-Key` vraća istu rezervaciju, a isti ključ za drugi termin daje `422`. Resurs je na automatskoj ili ručnoj potvrdi. Admin ili provider potvrđuje, odbija i označava dolazak ili nedolazak, a klijent može otkazati svoju rezervaciju do roka koji piše na resursu (`POST /api/bookings/{id}/confirm`, `/reject`, `/cancel`, `/check-in`, `/no-show`). Nedozvoljen prelaz, npr. potvrda otkazane rezervacije, vraća `409`. Svaka promjena statusa ide u historiju (`GET /api/bookings/{id}/events`), a korisnik vidi svoje rezervacije iz svih organizacija (`GET /api/bookings/mine`). Slobodni termini više ne nude zauzete. Nisam provjerio da li Render već vrti ovu verziju.

U Fazi 5 (u izradi) rezervacija na ručnoj potvrdi čeka providera 24 sata, a najkasnije do početka termina. Pozadinski posao svake minute prebaci isteklu u `EXPIRED` i oslobodi termin, a potvrđenu zatvori (`COMPLETED`) dan poslije kraja termina, da provider stigne označiti nedolazak. Ako posao kasni, nova rezervacija na istom resursu sama oslobodi istekle. Svaka promjena koju neko treba saznati (nova rezervacija na čekanju, potvrda, odbijanje, otkazivanje, istek) upisuje obavijest u tabelu `notifications`, u istoj transakciji kao promjena. Poseban posao svakih 30 sekundi šalje te obavijesti emailom. Ako SMTP ne odgovara, pokušava ponovo poslije 1, 5, 25 i 125 minuta, a poslije petog neuspjeha odustaje i zapiše razlog. Test šalje potvrdu rezervacije kroz pravi SMTP u Mailpit i provjerava naslov, tekst sa č i vrijeme u zoni organizacije. Lokalno se mailovi vide u Mailpitu na http://localhost:8025. Na Renderu SMTP još nije podešen, pa tamo obavijesti padaju i poslije pet pokušaja ostaju `FAILED`. To dolazi u Fazi 12.

Poslije registracije stiže mail s linkom za potvrdu emaila, koji važi 24 sata i može se iskoristiti jednom (`POST /api/auth/verify-email`, token u tijelu). Prijavljen korisnik može zatražiti novi link (`POST /api/me/email-verification`), a `GET /api/me` kaže da li je email potvrđen. Baza čuva samo hash tokena, a tijelo maila se briše čim je poslan. Nepotvrđen email za sada ništa ne zabranjuje. Link vodi na stranicu frontenda (`APP_URL`), koja još ne postoji.

| Faza | Šta | Status |
|---|---|---|
| 1 | Temelji i infrastruktura | gotovo |
| 2 | Auth i organizacije | gotovo |
| 3 | Resursi i dostupnost, slobodni termini | gotovo |
| 4 | Rezervacije i konkurentnost | gotovo |
| 5 | Pozadinski poslovi i email obavijesti | nije počelo |
| 6 | Statistika | nije počelo |
| 7 | Frontend (jezgro) | nije počelo |
| 8 | Sigurnost i GDPR | nije počelo |
| 9 | Observability i performanse | nije počelo |
| 10 | Napredne funkcije (lista čekanja, iCal, SSE...) | nije počelo |
| 11 | Plaćanje (Stripe, test mode) | nije počelo |
| 12 | Deploy i prezentacija | nije počelo |
| 13 | Završni pregled i dodaci | nije počelo |

Detaljan plan, model podataka i skica API-ja su u [PROJECT_PLAN.md](PROJECT_PLAN.md), odluke koje sam donio i zašto u [docs/ODLUKE.md](docs/ODLUKE.md), a slojevi i model podataka u [docs/ARHITEKTURA.md](docs/ARHITEKTURA.md).

Trenutno ima 245 testova i svi prolaze. JaCoCo mjeri 96,8 % linija glavnog koda (971 od 1003), izmjereno 14.11.2026. iz jednog čistog pokretanja. Najviše nepokrivenih linija ima `GlobalExceptionHandler` (9): handler za istovremenu izmjenu rezervacije (`@Version`), provjeren u servisnom testu, ali ne i kroz HTTP, i dio opisa stanja u poruci o nedozvoljenom prelazu. Nepokrivena je i grana u kojoj isti `Idempotency-Key` u istom trenutku stigne za dva različita resursa.

## Tehnologije

Backend je Java 21 sa Spring Bootom 4.1.2 (Hibernate 7.4), PostgreSQL 17 i Flywayom za migracije. API se dokumentira springdoc-om 3.2.0. Testovi koriste JUnit i Testcontainers, pa se vrte na pravom PostgreSQL-u u Dockeru, a ne na H2 ili na mojoj lokalnoj bazi. Pokrivenost mjeri JaCoCo, a CI na svaki push pokreće testove i gitleaks nad cijelom git historijom.

Frontend će biti React s Viteom i još nije počet. Backend radi na Renderu, a baza je na Neon-u. Vercel za frontend i Cloudflare R2 za slike su planirani i još nisu podešeni.

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

Za produkciju se uključuje profil `prod` (`SPRING_PROFILES_ACTIVE=prod`). Tada se baza spaja preko varijabli `DB_URL`, `DB_USER` i `DB_PASSWORD`, a port se uzima iz `PORT`. `JWT_SECRET` treba i tamo, i mora biti drugi ključ od lokalnog.

## Struktura

`backend/` je Spring Boot aplikacija, a paketi su po funkcionalnosti (`ba.slotbook.booking`, `ba.slotbook.resource`...), ne po sloju. `frontend/` još ne postoji, a u `docs/` su `ODLUKE.md` (odluke i zašto) i `ARHITEKTURA.md` (slojevi, tok zahtjeva i model podataka). `docker-compose.yml` diže PostgreSQL i Mailpit za lokalni razvoj. `.github/workflows/ci.yml` je CI, a `.github/dependabot.yml` sedmično traži zastarjele zavisnosti.

## Live demo i screenshotovi

Backend radi na https://slotbook-dxvs.onrender.com. Za sada ima samo [`/api/status`](https://slotbook-dxvs.onrender.com/api/status), [`/actuator/health`](https://slotbook-dxvs.onrender.com/actuator/health) i [Swagger UI](https://slotbook-dxvs.onrender.com/swagger-ui/index.html). Sve ostale adrese traže prijavu i bez nje vraćaju 401, pa i one koje ne postoje.

Pravog demoa još nema: demo računi stižu u Fazi 12, a screenshotovi kad bude frontenda. Free plan na Renderu uspavljuje servis poslije 15 minuta bez saobraćaja, pa prvi zahtjev poslije pauze traje oko minutu.

Repo je za sada privatan, pa CI značka iznad vjerovatno neće biti vidljiva nikome osim meni.
