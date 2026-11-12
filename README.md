# SlotBook

[![CI](https://github.com/eminahbabovich/SlotBook/actions/workflows/ci.yml/badge.svg)](https://github.com/eminahbabovich/SlotBook/actions/workflows/ci.yml)

Aplikacija za rezervaciju termina na zajedničkim resursima: učionice za učenje, sale za sastanke, konsultacije, teretana. Danas se to najčešće dogovara porukama u grupi ili tabelom, pa se termini preklapaju i niko ne zna ko je šta zauzeo. SlotBook treba da to riješi tako da dva korisnika ne mogu rezervirati isti termin, čak ni kad kliknu u istoj milisekundi. Svaka organizacija ima svoje resurse, osoblje, klijente i svoju vremensku zonu.

Ovo je moj drugi projekat nakon [RoomSplita](https://github.com/eminahbabovich/roomsplit). Backend učim od nule, pa je cilj da svaku liniju mogu objasniti. Učim uz AI mentora (Claude): on objašnjava i predlaže, a ja pokrećem, provjeravam i pitam dok ne razumijem.

## Status

Projekat je na početku. Faza 1 (infrastruktura) je gotova, a nijedna korisnička funkcionalnost još ne postoji.

U Fazi 1 je urađeno: Spring Boot projekat, PostgreSQL i Mailpit u Docker Composeu, prva Flyway migracija koja uključuje `btree_gist`, endpoint `GET /api/status`, globalno rukovanje greškama u formatu Problem Details (poruke na bosanskom), Swagger UI, CI na GitHub Actions (build, testovi, pokrivenost, skeniranje tajni), `Dockerfile` i profil `prod`. Provjerio sam i da `btree_gist` radi na Neon-u (PostgreSQL 17.11), pa `EXCLUDE` constraint ostaje plan za zaštitu od preklapanja. Backend je deployan na Render (free plan) s bazom na Neon-u i `GET /api/status` odgovara.

Faza 2 je u izradi. Za sada rade registracija (`POST /api/auth/register`), prijava s JWT access tokenom (`POST /api/auth/login`), `GET /api/me` i refresh tokeni u httpOnly kolačiću, s rotacijom i detekcijom ponovne upotrebe (`/api/auth/refresh`, `/api/auth/logout`). Prijavljen korisnik može napraviti organizaciju (`POST /api/organizations`) i tada postaje njen `ADMIN`, a javni profil organizacije se vidi bez prijave (`GET /api/organizations/{slug}`). Provjera uloga na endpointima, pozivnice i test izolacije organizacija dolaze.

| Faza | Šta | Status |
|---|---|---|
| 1 | Temelji i infrastruktura | gotovo |
| 2 | Auth i organizacije | u izradi |
| 3 | Resursi i dostupnost, slobodni termini | nije počelo |
| 4 | Rezervacije i konkurentnost | nije počelo |
| 5 | Pozadinski poslovi i email obavijesti | nije počelo |
| 6 | Statistika | nije počelo |
| 7 | Frontend (jezgro) | nije počelo |
| 8 | Sigurnost i GDPR | nije počelo |
| 9 | Observability i performanse | nije počelo |
| 10 | Napredne funkcije (lista čekanja, iCal, SSE...) | nije počelo |
| 11 | Plaćanje (Stripe, test mode) | nije počelo |
| 12 | Deploy i prezentacija | nije počelo |
| 13 | Završni pregled i dodaci | nije počelo |

Detaljan plan, model podataka i skica API-ja su u [PROJECT_PLAN.md](PROJECT_PLAN.md), a odluke koje sam donio i zašto u [docs/ODLUKE.md](docs/ODLUKE.md).

Trenutno ima 66 testova i svi prolaze. JaCoCo mjeri 97,9 % linija glavnog koda (283 od 289). Nepokrivene su dvije linije `main` metode, jedna u `SecurityConfig` i tri u `RefreshTokenService`.

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

`backend/` je Spring Boot aplikacija, a paketi su po funkcionalnosti (`ba.slotbook.status`, kasnije `booking`, `resource`...), ne po sloju. `frontend/` još ne postoji, a u `docs/` je za sada samo `ODLUKE.md`. `docker-compose.yml` diže PostgreSQL i Mailpit za lokalni razvoj. `.github/workflows/ci.yml` je CI, a `.github/dependabot.yml` sedmično traži zastarjele zavisnosti.

## Live demo i screenshotovi

Backend radi na https://slotbook-dxvs.onrender.com. Za sada ima samo [`/api/status`](https://slotbook-dxvs.onrender.com/api/status), [`/actuator/health`](https://slotbook-dxvs.onrender.com/actuator/health) i [Swagger UI](https://slotbook-dxvs.onrender.com/swagger-ui/index.html). Sve ostale adrese traže prijavu i bez nje vraćaju 401, pa i one koje ne postoje.

Pravog demoa još nema: demo računi stižu u Fazi 12, a screenshotovi kad bude frontenda. Free plan na Renderu uspavljuje servis poslije 15 minuta bez saobraćaja, pa prvi zahtjev poslije pauze traje oko minutu.

Repo je za sada privatan, pa CI značka iznad vjerovatno neće biti vidljiva nikome osim meni.
