# Plan portfolija

Cilj: 2 live projekta + portfolio sajt.
Backend: Spring Boot (učim). Frontend: React (znam).

Rad je podijeljen na **faze**, ne na sedmice. Faze se rade redom, ali tempo je
neujednačen: nekad se uradi veliki komad, nekad ništa. Faza je gotova kad je zadovoljen
njen kriterij "gotovo", a ne kad prođe neko vrijeme. Status svake faze vodi se u tabeli
ispod i u `README.md`.

**Projekat 1: RoomSplit** je gotov i live (frontend https://roomsplit-seven.vercel.app,
kod https://github.com/eminahbabovich/roomsplit). Njegov plan je ostao u tom repou.
Ovdje je samo ono što dolazi dalje.

---

# PROJEKAT 2: SlotBook (faze 1-14)

**Problem:** zajednički resursi (učionice za učenje, prostor za sastanke, termini
konsultacija, teretana) rezerviraju se preko poruka i tabela, uz preklapanja i zabune.
Aplikacija omogućava rezervaciju termina bez preklapanja, s ulogama, potvrdom i
obavijestima.

**Zašto je različit od P1:** vrijeme i konflikti (preklapanje termina), konkurentnost
(dva korisnika rezerviraju isti termin), uloge po organizaciji, zakazani poslovi, pouzdano
slanje emaila, upload slika, plaćanje, live ažuriranje, sigurnost i observability.
RoomSplit ništa od toga ne pokriva.

**Ambicija:** ne samo portfolio nego nešto što bi neko mogao stvarno koristiti. Zato plan
pored funkcionalnosti uključuje i ono što razlikuje demo od produkcije: rate limiting,
reset lozinke, GDPR, monitoring, backup, sigurnosne provjere i load test.

## Funkcionalnosti

### Jezgro (MVP)

1. Organizacije (multi-tenant): svaka ima svoje resurse, osoblje i klijente i svoju
   vremensku zonu (npr. teretana, salon, fakultetska biblioteka). Članovi se dodaju
   pozivnicom na email.
2. Uloge po organizaciji: `ADMIN`, `PROVIDER` (vlasnik resursa), `CLIENT`. Isti korisnik
   može biti `PROVIDER` u jednoj organizaciji, a `CLIENT` u drugoj.
3. Resursi (prostor ili osoba): trajanje termina, minimalni rok za otkazivanje,
   automatska ili ručna potvrda, slika, opcionalno cijena.
4. Dostupnost: sedmično radno vrijeme + izuzeci (godišnji, praznici).
5. Kalendar slobodnih termina. Termini se računaju iz pravila, ne spremaju se unaprijed.
6. Rezervacija bez preklapanja, čak i kad dva zahtjeva stignu istovremeno. Ponovljeni
   zahtjev (dupli klik, loša mreža) ne pravi dvije rezervacije (`Idempotency-Key`).
7. Životni ciklus rezervacije (vidi tabelu prelaza ispod), s historijom promjena.
   Otkazivanje s pravilom (npr. ne manje od 2h prije termina).
8. Pozadinski poslovi (`@Scheduled`): istek nepotvrđenih rezervacija, automatsko
   zatvaranje završenih, podsjetnici.
9. Email obavijesti preko tabele `notifications` (pouzdano slanje, ponovni pokušaj),
   uključujući pozivnice, verifikaciju emaila i reset lozinke.
10. Upload slike resursa (S3-kompatibilno skladište).
11. Statistika za providera i admina (broj rezervacija, popunjenost, otkazivanja,
    no-show, najtraženiji termini).
12. Demo mod: seed podaci i demo računi, da posjetilac portfolija odmah vidi cijeli tok.

### Produkcijski sloj

13. Sigurnost: rate limiting, zaključavanje naloga nakon više pogrešnih prijava, sigurnosna
    zaglavlja, stroga CORS konfiguracija, audit log, rotacija refresh tokena s detekcijom
    ponovne upotrebe.
14. GDPR: korisnik može izvesti svoje podatke i obrisati nalog (anonimizacija, statistika
    ostaje tačna).
15. Observability: health i readiness endpointi, metrike, strukturirani logovi s
    ID-jem zahtjeva, praćenje grešaka, uptime monitor.
16. Pouzdanost: backup baze s provjerenim oporavkom, `RUNBOOK.md`, load test s
    objavljenim brojkama.

### Napredne funkcije

17. Lista čekanja: kad se termin otkaže, prvi na listi dobija obavijest s rokom za potvrdu.
18. Ponavljajuće rezervacije (svake sedmice, N puta).
19. Live ažuriranje kalendara preko SSE.
20. iCal pretplata: klijent doda svoje rezervacije u Google/Apple kalendar.
21. QR check-in na termin (provider skenira, status prelazi u `COMPLETED` ili ostaje
    `NO_SHOW`).
22. Resursi s kapacitetom veće od 1 (grupni trening, radionica). Ovo traži drugačiji
    mehanizam od `EXCLUDE` constrainta, vidi odluke.
23. Plaćanje (Stripe, samo test mode): depozit ili predplata, webhook, povrat novca pri
    otkazivanju po pravilu.

## Životni ciklus rezervacije

| Iz | U | Ko / šta pokreće |
|---|---|---|
| (novo) | `PENDING` | klijent, resurs je na ručnoj potvrdi ili čeka plaćanje |
| (novo) | `CONFIRMED` | klijent, resurs je na automatskoj potvrdi |
| `PENDING` | `CONFIRMED` | provider/admin potvrdi, ili uspješno plaćanje |
| `PENDING` | `REJECTED` | provider/admin odbije |
| `PENDING` | `EXPIRED` | pozadinski posao, istekao `expires_at` |
| `PENDING`, `CONFIRMED` | `CANCELLED` | klijent (poštujući rok) ili provider/admin |
| `CONFIRMED` | `COMPLETED` | check-in, ili pozadinski posao nakon završetka termina |
| `CONFIRMED` | `NO_SHOW` | provider označi da klijent nije došao |

Sva ostala kretanja su zabranjena i vraćaju `409`. Termin zauzimaju samo `PENDING` i
`CONFIRMED`; ostala stanja ga oslobađaju.

## Model podataka (početna verzija, doradit ćemo uz Claude-a)

- `users` (id, email UNIQUE, password_hash, display_name, email_verified_at, locale,
  failed_login_count, locked_until, deleted_at, created_at)
- `user_tokens` (id, user_id, type [`EMAIL_VERIFY`, `PASSWORD_RESET`], token_hash,
  expires_at, used_at)
- `refresh_tokens` (id, user_id, family_id, token_hash, expires_at, revoked_at, replaced_by)
- `organizations` (id, name, slug UNIQUE, timezone, created_at)
- `organization_members` (organization_id, user_id, role, joined_at) - PK (organization_id, user_id)
- `organization_invites` (id, organization_id, email, role, token_hash, expires_at, accepted_at)
- `resources` (id, organization_id, provider_id, name, description, image_key,
  slot_minutes, min_notice_hours, auto_confirm, capacity DEFAULT 1, price_amount,
  currency, active)
- `availability_rules` (id, resource_id, day_of_week, start_time TIME, end_time TIME)
- `time_off` (id, resource_id, starts_at TIMESTAMPTZ, ends_at TIMESTAMPTZ, reason)
- `bookings` (id, resource_id, client_id, series_id, start_time TIMESTAMPTZ,
  end_time TIMESTAMPTZ, status, note, expires_at, idempotency_key, version, created_at,
  cancelled_at, cancel_reason) - UNIQUE (client_id, idempotency_key)
- `booking_events` (id, booking_id, from_status, to_status, actor_id, created_at, details)
- `booking_series` (id, resource_id, client_id, rule, created_at)
- `waitlist_entries` (id, resource_id, client_id, start_time, end_time, status,
  offered_until, created_at)
- `notifications` (id, user_id, type, booking_id, status, attempts, next_attempt_at,
  created_at, sent_at). Umjesto `payload JSONB` za sada `booking_id`, vidi odluke.
- `payments` (id, booking_id, provider_ref, amount, currency, status, created_at)
- `processed_webhook_events` (event_id PK, received_at)
- `audit_log` (id, organization_id, actor_id, action, target_id, detail, created_at). Bez
  `entity_type` i JSONB-a, razlog je u `docs/ODLUKE.md`.
- `shedlock`: ne koristimo, poslovi su sigurni i na dvije instance (vidi `docs/ODLUKE.md`)

Invarijante:
- Dvije aktivne rezervacije (`PENDING`, `CONFIRMED`) istog resursa se ne smiju preklapati.
  Ovo čuva baza (`EXCLUDE` constraint), ne samo Java kod.
- Rezervacija mora cijela stati u radno vrijeme resursa i ne smije pasti u `time_off`.
- Vrijeme se u bazi čuva kao `TIMESTAMPTZ` (UTC), a radno vrijeme kao lokalno vrijeme
  u zoni organizacije. Zato ljetno/zimsko računanje vremena mora raditi ispravno.
- Svaka promjena statusa rezervacije upisuje red u `booking_events`, u istoj transakciji.
- Svaki upit nad podacima organizacije je ograničen na organizaciju iz tokena korisnika.

## API (skica)

| Metoda | Putanja | Opis |
|---|---|---|
| POST | /api/auth/register, /login, /refresh, /logout | prijava s refresh tokenom |
| POST | /api/auth/verify-email, /forgot-password, /reset-password | verifikacija i reset |
| GET/DELETE | /api/me, /api/me/export | profil, izvoz podataka, brisanje naloga |
| POST | /api/organizations | nova organizacija (kreator postaje `ADMIN`) |
| GET | /api/organizations/{slug} | javni profil organizacije |
| GET | /api/organizations/{id}/members | članovi s ulogama (`ADMIN`) |
| POST | /api/organizations/{id}/invites | pozivnica na email s ulogom (`ADMIN`) |
| POST | /api/invites/accept | prihvati pozivnicu (token u tijelu, ne u putanji, da ne ostaje u logovima) |
| POST/GET/PUT/DELETE | /api/organizations/{id}/resources | CRUD resursa (`ADMIN`, `PROVIDER`) |
| PUT | /api/resources/{id}/availability | sedmično radno vrijeme |
| POST | /api/resources/{id}/time-off | izuzetak (godišnji, praznik) |
| POST | /api/resources/{id}/image | upload slike |
| GET | /api/resources/{id}/slots?from=&to= | slobodni termini |
| POST | /api/resources/{id}/bookings | nova rezervacija (`409` ako je zauzeto) |
| GET | /api/bookings/mine | moje rezervacije (s paginacijom) |
| GET | /api/bookings/{id}/events | historija statusa |
| POST | /api/bookings/{id}/confirm, /reject, /cancel, /check-in, /no-show | prelazi stanja |
| POST | /api/resources/{id}/waitlist | upis na listu čekanja za zauzet termin |
| DELETE | /api/waitlist/{id} | ispis sa liste (samo svoj upis) |
| GET | /api/me/calendar.ics?token= | iCal pretplata |
| GET | /api/organizations/{id}/events | SSE tok promjena termina |
| POST | /api/bookings/{id}/pay, /api/webhooks/stripe | plaćanje i webhook |
| GET | /api/organizations/{id}/stats | statistika (`ADMIN`, `PROVIDER`) |
| GET | /api/organizations/{id}/audit-log | audit log (`ADMIN`) |
| GET | /actuator/health | health check za deploy i uptime monitor |

Konvencije: greške u jedinstvenom formatu (Problem Details, RFC 9457; Spring ga podržava,
verziju potvrđujemo u Fazi 1), sve liste s paginacijom, datumi uvijek ISO 8601 sa zonom,
svaki endpoint opisan u Swaggeru.

## Faze

| Faza | Naziv | Fokus | Šta ja učim | Status |
|---|---|---|---|---|
| 1 | Temelji i infrastruktura | Spring Boot projekat, Docker Compose (Postgres + Mailpit), Flyway, **Testcontainers**, **GitHub Actions** (build + test), springdoc, globalni `@RestControllerAdvice` s Problem Details, profil `prod` (`demo` stiže u Fazi 12, a lokalni razvoj je podrazumijevani profil jer je `application-local.*` u `.gitignore`), `.env.example`, `Dockerfile` (multi-stage), JaCoCo, Dependabot, prvi endpoint + test, **provjera `btree_gist` na Neon-u**, hodajući kostur na live adresi | Testcontainers, CI pipeline, Compose, profili, zašto testovi ne smiju ovisiti o mojoj lokalnoj bazi | gotovo |
| 2 | Auth i organizacije | Korisnici, BCrypt, JWT + **refresh tokeni** (rotacija + detekcija ponovne upotrebe preko `family_id`), organizacije, uloge po organizaciji, pozivnice, `@PreAuthorize` s vlastitim provjerama članstva | Method security, `PermissionEvaluator` / bean u SpEL, refresh token tok, autorizacija koja ovisi o resursu | gotovo |
| 3 | Resursi i dostupnost | CRUD resursa, sedmična pravila, `time_off`, **algoritam za slobodne termine**, upload slike kroz `StorageService` (lokalno + S3-kompatibilno), paginacija | `java.time` (`Instant`, `ZonedDateTime`, DST), `TIMESTAMPTZ`, interface + dvije implementacije, `MultipartFile` | gotovo |
| 4 | Rezervacije i konkurentnost | Kreiranje rezervacije, **`EXCLUDE` constraint**, stanja i prelazi, `booking_events`, `Idempotency-Key`, pravilo otkazivanja, `@Version`, **test sa 20 niti** | PostgreSQL range tipovi, transakcije i izolacija, optimistično vs pesimistično zaključavanje, `409 Conflict`, state machine, idempotentnost | gotovo |
| 5 | Pozadinski poslovi i obavijesti | `@Scheduled` istek `PENDING` i zatvaranje završenih, podsjetnici, `notifications` tabela (outbox), slanje emaila, `@TransactionalEventListener`, ponovni pokušaj s backoffom, verifikacija emaila, reset lozinke, slanje pozivnica, `.ics` prilog u potvrdi | Scheduling, zašto se email ne šalje unutar iste transakcije, `AFTER_COMMIT`, idempotentnost, ShedLock, sigurni tokeni za reset | gotovo |
| 6 | Statistika | Agregatni upiti (`GROUP BY`, popunjenost, otkazivanja i no-show po periodu, najtraženiji termini dan x sat), endpointi za providera i admina, CSV izvoz | JPQL/native upiti, projekcije u DTO, indeksi i `EXPLAIN` | gotovo |
| 7 | Frontend (jezgro) | **Prvo biramo vizuelni smjer i fontove** (vidi CLAUDE.md). Prijava, izbor organizacije, kalendar slobodnih termina, tok rezervacije, moje rezervacije, kalendar providera, admin (članovi, resursi, radno vrijeme), dashboard sa grafovima, vrijeme u zoni organizacije, pristupačnost, mobilni prikaz, Vitest + Playwright za glavni tok | React kalendar biblioteka, zaštićene rute po ulozi, obrada `409`, refresh token u klijentu (jedan refresh za više paralelnih zahtjeva), e2e testiranje | gotovo |
| 8 | Sigurnost i GDPR | Rate limiting (Bucket4j), zaključavanje naloga, sigurnosna zaglavlja, CORS, `audit_log`, izvoz i brisanje naloga, zaštita od enumeracije emailova, OWASP Top 10 prolaz, CodeQL + skeniranje zavisnosti + gitleaks u CI-ju, `docs/SIGURNOST.md`, **test koji prođe kroz sve endpointe s tokenom iz tuđe organizacije** | Napadi koji se stvarno dešavaju (IDOR, brute force, enumeracija), rate limiting algoritmi, anonimizacija, šta je tajna a šta nije | gotovo |
| 9 | Observability i performanse | Actuator (health, readiness), Micrometer metrike (rezervacije, konflikti, neuspjeli mailovi), JSON logovi s ID-jem zahtjeva (MDC), praćenje grešaka (Sentry free), indeksi + `EXPLAIN`, traženje N+1 upita, **k6 load test** s stvarnim brojkama, `RUNBOOK.md` s **provjerenim** backup/restore postupkom | Metrike vs logovi, kako se nađe spor upit, connection pool, šta load test zaista pokazuje | gotovo |
| 10 | Napredne funkcije | Redom po vrijednosti: lista čekanja, iCal pretplata, SSE, ponavljajuće rezervacije, QR check-in, resursi s kapacitetom > 1. Svaka s vlastitim testom i UI-jem | SSE i `SseEmitter`, pravila ponavljanja, delimični konflikti u seriji, drugačiji mehanizam zaštite kad `EXCLUDE` ne vrijedi | gotovo |
| 11 | Plaćanje (Stripe, test mode) | Cijena na resursu, Checkout, `PENDING` do uplate, webhook s provjerom potpisa, **idempotentna obrada webhooka**, povrat pri otkazivanju po pravilu, istek neplaćenih | Webhookovi, zašto se isti događaj može stići dvaput, razlika između plaćanja i potvrde, rad s eksternim API-jem u testovima | u izradi |
| 12 | Deploy i prezentacija | Deploy (Render/Neon/Vercel + R2 za slike), demo seed i demo računi (profil `demo`, noćni reset demo podataka), uptime monitor, README, `docs/`, screenshotovi i kratki GIF toka, CI badge, poddomen | Env varijable u produkciji, profili (`demo`), skladište slika, health check, hladni start besplatnih planova | ⏳ nije počelo |
| 13 | Završni pregled i dodaci | Pregled cijelog koda (`/code-review`), čišćenje mrtvog koda, prolaz kroz README i `docs/` da sve što piše stvarno radi, engleski prijevod UI-ja, i **lista dodataka koje smo usput zapisali**: ovdje odlučujemo šta još ulazi | Kako se čita tuđi (svoj stari) kod, šta bih prvo mijenjao | ⏳ nije počelo |
| 14 | AI asistent za rezervacije (chatbot) | Chat endpoint koji zove LLM s **tool calling**, alati preko postojećih servisa (resursi, slobodni termini, rezervacija, otkazivanje, moje rezervacije), potvrda prije rezervacije i otkazivanja koju čuva server, historija razgovora po sesiji, ključ samo iz env-a, chat widget na stranici za rezervaciju, `docs/AI_ASISTENT.md` | Kako radi petlja alata (model traži alat, server ga izvrši, rezultat ide nazad), zašto model ne smije sam odlučivati ko je korisnik, mockanje eksternog API-ja u testu, trošak i limiti LLM poziva | ⏳ nije počelo |

Pravilo iz P1 ostaje: ne čekati kraj s deployem. Zato u Fazi 1 uradimo "hodajući kostur"
(prazan endpoint na live adresi), a u Fazi 12 samo punimo ono što već radi.

**Ako vrijeme stisne:** faze 1-9 i 12 su jezgro. Ako treba birati, Faza 12 se radi prije
faza 10 i 11, jer je live projekat s čvrstim jezgrom vrijedniji od nedovršenih dodataka.
Unutar faze 10 redoslijed je već po vrijednosti, pa se ono s kraja liste može izostaviti.
Faza 14 je dodatak izvan jezgra i ne smije ga mijenjati: ako je nema, aplikacija je ista.
Ako se radi, radi se prije Faze 13, da završni pregled obuhvati i nju.

## Kriterij "gotovo" po fazi

1. CI je zelen na GitHubu, testovi se vrte na Testcontainers Postgresu, `btree_gist`
   potvrđen na Neon-u, greške se vraćaju u jedinstvenom formatu.
2. Refresh tok radi, ponovna upotreba starog refresh tokena poništava cijelu porodicu
   tokena, korisnik iz organizacije A ne može dotaknuti podatke organizacije B (postoji
   test koji to dokazuje).
3. Slobodni termini su tačni preko prelaska na ljetno vrijeme (test), slika se
   uploaduje i vraća kroz API.
4. Test pokrene 20 niti na isti termin i prođe tačno jedna rezervacija, ostale dobiju
   `409`. Isti `Idempotency-Key` poslan dvaput vraća istu rezervaciju. Nedozvoljeni prelaz
   stanja vraća `409`.
5. Istekla `PENDING` rezervacija prelazi u `EXPIRED`, a email se šalje i nakon greške
   providera (ponovni pokušaj), bez gubitka rezervacije. Reset lozinke radi, a token se ne
   može iskoristiti dvaput.
6. Statistika se poklapa s ručno izračunatim brojkama iz testnih podataka.
7. Cijeli tok (prijava, biranje termina, rezervacija, otkazivanje) radi u UI-ju, a
   konflikt se prikaže prijateljskom porukom. Glavni tok pokriva Playwright test.
8. Test prolaza kroz sve endpointe s tuđim tokenom je zelen, rate limiting je dokazan
   testom, izvoz i brisanje naloga rade, CI skenira zavisnosti i tajne.
9. `/actuator/health` i metrike rade, svaki zahtjev ima ID u logu, k6 rezultati su
   zapisani u `docs/` s opisom mašine, backup je stvarno vraćen u praznu bazu.
10. Svaka napredna funkcija ima test i radi u UI-ju. Kapacitet > 1 ima test konkurentnosti.
11. Plaćanje u test modu radi do kraja, isti webhook poslan dvaput ne duplira uplatu,
    neispravan potpis se odbija.
12. Live URL radi, demo računi rade, README i `docs/` su ažurni.
13. Pregled koda je urađen, nalazi su ili ispravljeni ili zapisani u `docs/ODLUKE.md`
    kao svjesno ostavljeni, lista dodataka je zatvorena.
14. Kroz chat se može naći slobodan termin, rezervisati ga i otkazati. Rezervacija i
    otkazivanje se ne dese bez potvrde korisnika, i to čuva server, ne samo prompt.
    Rezervacija iz chata ide kroz isti `BookingService` kao obična (isti `409`, ista
    izolacija organizacija), što dokazuje integracioni test s mockanim LLM klijentom.
    Testovi prolaze bez API ključa, ključ nije u repou (gitleaks), a timeout LLM-a daje
    prijateljsku poruku umjesto `500`.

## Faza 7 po koracima

Smjer je Red vožnje (`docs/DIZAJN.md`). Svaki ekran se prije "gotovo" pogleda na 375 i
1280 px i ima učitavanje, prazno stanje, grešku i uspjeh.

1. **Kostur i fontovi.** Vite + React u JavaScriptu (TypeScript ne), tri fonta preko Fontsource,
   provjera da crtaju č, ć, š, đ, ž u browseru. Gotovo 15.11.2026.
2. **Tokeni i Vitest.** Boje, razmaci, fontovi i zaobljenja iz `DIZAJN.md` kao CSS
   varijable na jednom mjestu, osnovni stilovi, Vitest s prvim testom (boje u kodu su
   tačno one iz `DIZAJN.md`). Testing Library dolazi s prvim testom komponente. Gotovo
   15.11.2026.
3. **API sloj.** `apiFetch`, `ApiError` iz Problem Detailsa, prva funkcija
   (`getStatus`), Vite proxy na backend, testovi koji čuvaju imena polja iz DTO-ova
   (nema TypeScripta da ih čuva). Gotovo 15.11.2026. Odluka TanStack Query ili vlastiti
   hook je pomjerena u korak 5, gdje prvi ekran učitava podatke.
4. **Prijava, registracija i refresh.** Access token u memoriji, refresh kolačić, jedan
   refresh za više paralelnih `401` (test). Ekrani prijave, registracije i privremeni
   ekran naloga. Gotovo 15.11.2026, provjereno s lažnim backendom, s pravim ne.
5. **Izbor organizacije i rute.** Router, zaštićene rute po ulozi, izbor organizacije.
   Odluka: TanStack Query ili vlastiti hook za učitavanje podataka. Gotovo 15.11.2026:
   React Router i TanStack Query, lista organizacija s ulogom, forma za novu kad je lista
   prazna, novi endpoint `GET /api/me/organizations`. Rute čuva prijava, a ne uloga, jer
   još nema rute samo za admina ili providera. Čuvar po ulozi dolazi s prvom takvom rutom
   (korak 9 ili 10). Provjereno s lažnim backendom, s pravim ne.
6. **Kalendar slobodnih termina.** Dan i lista na telefonu, zona organizacije, dan
   prelaska na ljetno vrijeme, tastatura, stanja bez boje. Gotovo 15.11.2026: `/slots`
   dobio listu `taken` s mojim statusom, kalendar je jedan dan i na desktopu, vrijeme kroz
   `Intl` i vlastite bosanske nazive. Izbor termina za sada samo kaže koji je termin
   izabran, potvrda dolazi u koraku 7. Provjereno s lažnim backendom u Edgeu, s pravim ne.
7. **Tok rezervacije.** Potvrda, `Idempotency-Key` po namjeri, `409` s najbližim
   slobodnim terminom. Gotovo 15.11.2026: potvrda ispod termina, ključ kad se termin
   izabere, najbliži slobodan iz učitane sedmice (frontend, bez promjene backenda).
   Provjereno s lažnim backendom u Edgeu, s pravim ne.
8. **Moje rezervacije i otkazivanje.** Rok za otkazivanje iz odgovora, potvrda prije
   otkazivanja. Gotovo 15.11.2026: `GET /api/bookings/mine` dobio `cancelDeadline` i
   `organizationName`, ekran `/rezervacije` s nadolazećim i prošlim, otkazivanje uz
   potvrdu. Provjereno s lažnim backendom u Edgeu, s pravim ne.
9. **Kalendar providera.** Potvrdi, odbij, nije došao. Gotovo 15.11.2026: novi
   `GET /api/resources/{id}/bookings?from&to` (samo admin i provider resursa, s imenom
   klijenta), ekran `/o/{slug}/r/{id}/raspored` po danima, link "Raspored" samo za one koji
   vode resurs. Provjereno s lažnim backendom u Edgeu, s pravim ne.
10. **Admin.** Članovi i pozivnice, resursi, radno vrijeme i izuzeci. Gotovo 18.11.2026, u
    tri dijela. 10a: lista članova i pozivnica s ulogom (samo admin). 10b: stranica za link iz
    pozivnice (prihvata se na klik, token preživi prijavu i registraciju), novi resurs i izmjena
    (PUT šalje sva polja), deaktivacija uz potvrdu, promjena osobe koja vodi resurs. 10c:
    sedmično radno vrijeme jednim PUT-om s greškom ispod pravog intervala, izuzeci s pomakom
    zone izračunatim na frontendu (sat koji ne postoji na dan prelaska na ljetno forma odbije).
    Provjereno s lažnim backendom u Edgeu na 375 i 1280 px, s pravim ne.
11. **Dashboard statistike.** Grafovi iz Faze 6 i CSV izvoz. Gotovo 18.11.2026. Stranica
    `/o/{slug}/statistika` za admina i osobu koja vodi termine, mjesec po mjesec (mjesec je u
    adresi i računa se u zoni organizacije). Tri tabele s trakama: rezervacije po stanju,
    popunjenost po resursu (ukupno iz minuta, ne prosjek procenata) i pet najtraženijih termina.
    CSV pravi backend, a frontend ga preuzima kao bajtove, da ostane BOM. Provjereno s lažnim
    backendom u Edgeu na 375 i 1280 px, uključujući preuzet fajl. S pravim backendom nije.
12. **Playwright glavni tok i kraj faze.** Prijava, termin, rezervacija, otkazivanje,
    konflikt. README, `docs/` i status. Gotovo 19.11.2026. Dva testa u `frontend/e2e/` idu kroz
    pravi backend i svoju bazu u Dockeru (`npm run e2e`): klijent rezerviše i otkaže termin pa
    ga kalendar opet nudi, i termin koji admin uzme dok klijent gleda potvrdu daje `409` s
    najbližim slobodnim. Podaci se prave kroz API, token pozivnice se čita iz Mailpita, a browser
    je u drugoj zoni od organizacije. Raspored, admin i statistika s pravim backendom u browseru
    nisu provjereni. Test nije u CI-ju.

## Faza 8 po koracima

Svaki korak je jedna stvar, bez dijelova a, b i c. Kriterij "gotovo" je broj 8 iznad. Gitleaks u
CI-ju i Dependabot postoje od Faze 1, a rotacija refresh tokena s detekcijom ponovne upotrebe od Faze 2.

1. **Pristup po ulogama kroz sve endpointe.** Test izolacije iz Faze 2 provjerava samo admina druge
   organizacije. Dobija i uloge iz iste organizacije (klijent, provider drugog resursa, klijent tuđe
   rezervacije) s očekivanim ishodom za svaku rutu, a čuvar gleda sve `/api` rute, ne samo one s ID-jem
   organizacije, resursa ili rezervacije u putanji. Gotovo 19.11.2026. Klijent i provider drugog stola
   prolaze istih 25 zahtjeva kao tuđi admin, bez tokena je svuda `401`, a pomiješani ID-jevi (svoja
   organizacija, tuđi sto) ništa ne mijenjaju. Čuvar gleda sve `/api` rute, a 15 koje nisu podaci
   organizacije imaju razlog u testu. Nije pronađena nijedna rupa, a 5 mutacija obori test.
2. **Rate limiting prijave i registracije.** Bucket4j (prvo provjeriti verziju za Spring Boot 4.1),
   `429` s `Retry-After` i Problem Details, test koji dokaže granicu. Gotovo 19.11.2026. Bucket4j 8.22.0
   (samo core), 20 prijava u minuti i 20 registracija na sat po IP adresi, bucketi u memoriji uz
   čišćenje punih svakih 10 minuta. Na Renderu se adresa čita iz `CF-Connecting-IP`, jer
   `X-Forwarded-For` bira pozivalac. To na Renderu nije provjereno.
3. **Rate limiting mailova.** Reset lozinke, novi link za potvrdu emaila i pozivnice, da se nečiji
   inbox ne može zatrpati (dug iz Faze 5). Gotovo 19.11.2026. Najviše 3 maila iste vrste istom
   primaocu na sat, brojano iz tabele `notifications`. Reset lozinke preko granice tiho ne pošalje
   mail, novi link i pozivnica vraćaju `429`. Istovremeni zahtjevi mogu proći granicu (nema
   zaključavanja), to je zapisano u `docs/ODLUKE.md`.
4. **Zaključavanje naloga.** Poslije više pogrešnih prijava nalog se zaključa na neko vrijeme, a
   odgovor ne otkriva da li nalog postoji. Gotovo 19.11.2026. 5 pogrešnih lozinki u 15 minuta
   zaključa prijavu za taj email na 15 minuta (`429` s `Retry-After`), i za email bez naloga.
   Brojanje je u tabeli `login_failures`, upis greške pod `FOR UPDATE`. Ograničenje: neko ko zna
   tuđi email može tu osobu držati zaključanom, zapisano u `docs/ODLUKE.md`.
5. **Enumeracija emailova.** Registracija vraća isti odgovor i kad nalog postoji, a vlasnik emaila
   dobije mail (odluka iz Faze 2). Izmjeriti i razliku u vremenu odgovora za reset lozinke (dug iz Faze 5).
   Gotovo 19.11.2026. Registracija uvijek vraća `202` bez tijela, a prijava radi tek poslije potvrde
   emaila, jer bi inače registracija pa prijava otkrila ko ima nalog. Izmjereno kroz MockMvc: reset
   lozinke za postojeći email je sporiji za 6 do 15 ms, to ostaje otvoreno u `docs/ODLUKE.md`.
6. **Sigurnosna zaglavlja i CORS.** CSP, `X-Content-Type-Options`, `Referrer-Policy`, HSTS na
   produkciji, CORS samo za adresu frontenda, test za zaglavlja. Gotovo 19.11.2026. Zaglavlja su na
   odgovorima backenda, a CSP za stranice frontenda ide s hostom frontenda u Fazi 12. HSTS se šalje
   uvijek, jer je iza Renderovog proxyja svaki zahtjev za Spring običan HTTP. Na Renderu nije provjereno.
7. **Audit log.** Tabela `audit_log` i upis za osjetljive radnje (uloge, pozivnice, resursi, brisanje
   naloga), a admin ga čita za svoju organizaciju. Gotovo 19.11.2026. Upisuju se nova organizacija,
   poslana i prihvaćena pozivnica, i nov, izmijenjen, prebačen ili deaktiviran resurs, u istoj
   transakciji kao radnja. Brisanje naloga dolazi s korakom 9. Radno vrijeme, izuzeci i slika resursa
   se ne upisuju.
8. **Izvoz podataka.** `GET /api/me/export`: sve što aplikacija zna o korisniku, u JSON-u.
   Gotovo 19.11.2026. Profil, članstva, rezervacije i njihova historija, resursi koje vodi, obavijesti
   bez tijela, sesije, linkovi iz maila, pozivnice, neuspjele prijave i njegovi zapisi u dnevniku.
9. **Brisanje naloga.** Anonimizacija umjesto brisanja redova, da statistika i rasporedi ostanu
   tačni. Refresh tokeni se gase, a budući termini otkazuju. Gotovo 21.11.2026. `DELETE /api/me` traži
   lozinku. Jedini admin organizacije i provider koji vodi resurs (i neaktivan) dobijaju `409` s
   nazivima. Access token obrisanog naloga važi još do 15 minuta, to je zapisano u `docs/ODLUKE.md`.
10. **Frontend za Fazu 8.** Zaključan nalog, `429`, izvoz i brisanje naloga u UI-ju. Gotovo 21.11.2026.
   Registracija vodi na ekran "pogledaj inbox" umjesto u aplikaciju, link iz maila otvara
   `/potvrda-emaila`, `429` kaže u koliko sati je sljedeći pokušaj, a `/nalog` ima izvoz i brisanje s
   lozinkom i drugim klikom. Usput nađena greška: `202` bez tijela je rušio `apiFetch`.
11. **CI: CodeQL, zavisnosti i frontend.** CodeQL za Javu i JavaScript, skeniranje zavisnosti, i job
    s Vitestom i Oxlintom (do sada CI ne pokreće ništa iz frontenda). Gotovo 22.11.2026. Repo je
    postao javan, jer CodeQL na privatnom repou traži plaćenu licencu. CI (backend, frontend, tajne) i
    CodeQL za Javu i JavaScript su zeleni za `2051da1`. Prvi CodeQL je pao jer je u postavkama bio
    uključen i default setup, koji odbija rezultate iz workflowa.
12. **OWASP Top 10 i `docs/SIGURNOST.md`, kraj faze.** Za svaku stavku šta nas štiti, kojim testom je
    dokazano i šta nije pokriveno. README i status. Gotovo 22.11.2026, po listi OWASP Top 10:2025.

## Faza 9 po koracima

Kriterij "gotovo" je gore (tačka 9): health i metrike rade, svaki zahtjev ima ID u logu, k6 rezultati
su zapisani s opisom mašine, backup je stvarno vraćen u praznu bazu.

1. **ID zahtjeva u logu i JSON logovi.** Filter daje svakom zahtjevu ID (`X-Request-Id`, MDC
   `requestId`), lokalno u običnom logu, a u prod profilu je log JSON (ECS). Isti ID je i u tijelu
   greške `500`. Gotovo 22.11.2026.
2. **Health i readiness.** `/actuator/health/liveness` i `/readiness`, baza u readiness, Render gleda
   readiness. Gotovo 22.11.2026.
3. **Metrike.** Micrometer brojači za napravljene rezervacije, sudare (`409`) i neuspjele mailove.
   Endpoint metrika nije javan. Gotovo 22.11.2026.
4. **Praćenje grešaka.** Neočekivane greške idu u Sentry (free plan) s ID-jem zahtjeva. Bez DSN-a ne
   šalje ništa, pa lokalno i u testovima ostaje ugašen. Gotovo 22.11.2026 u kodu, a na Renderu čeka
   da se napravi projekat u Sentryju i postavi `SENTRY_DSN`.
5. **N+1 i spori upiti.** Broj SQL upita po zahtjevu za glavne liste mjeren u testu, popravka gdje
   raste s brojem redova, `EXPLAIN` za upite koji to traže. Gotovo 22.11.2026: N+1 nije nađen, raspored
   providera dobio donju granicu (`V33`).
6. **k6 load test.** Skripta za slobodne termine, rezervaciju i sudar na istom terminu, lokalno. Brojke,
   mašina i pul konekcija u `docs/PERFORMANSE.md`. Gotovo 22.11.2026.
7. **Backup i `docs/RUNBOOK.md`.** `pg_dump`, vraćanje u praznu bazu i provjera broja redova, pa isti
   postupak za Neon. Gotovo 22.11.2026, lokalno i s Neon-a.
8. **Kraj faze.** README, status, ODLUKE. Gotovo 22.11.2026.

## Faza 10 po koracima

Kriterij "gotovo" je gore (tačka 10): svaka napredna funkcija ima test i radi u UI-ju, kapacitet > 1 ima
test konkurentnosti. Redoslijed je po vrijednosti, pa se s kraja može odustati ako vrijeme stisne
(tada ide u Dodatke, a Faza 12 ide prije).

1. ✅ **Lista čekanja, backend** (22.11.2026). Klijent se upiše na zauzet termin (`POST /api/resources/{id}/waitlist`) i
   ispiše (`DELETE /api/waitlist/{id}`). Kad se termin oslobodi (otkazivanje, odbijanje, istek), posao ponudi termin prvom
   na listi: mail i 30 minuta prednosti, a za to vrijeme drugi ne mogu rezervisati (`409`). Ako ga ne
   uzme, ponuda istekne i ide sljedeći. Preklapanje i dalje čuva `EXCLUDE`, a prednost Java pod
   lockom resursa.
2. ✅ **Lista čekanja, UI** (22.11.2026). Na zauzetom terminu dugme "Javi mi ako se oslobodi", moje liste čekanja,
   ponuđen termin istaknut u kalendaru. Za to backend još treba: `GET` mojih upisa i u `/slots`
   oznaku da je termin ponuđen meni ili nekom drugom (sad ga kalendar prikaže kao slobodan, a
   rezervacija vrati `409`). Uz to lista čekanja ide u izvoz podataka (`/api/me/export`).
3. ✅ **iCal pretplata** (22.11.2026). Tajni link po korisniku (`.ics` bez prijave, token se može promijeniti), moje
   rezervacije u Google/Apple kalendaru. Dugme za kopiranje linka u UI-ju.
4. ✅ **Live kalendar (SSE)** (22.11.2026). Kad neko rezerviše ili otkaže, otvoreni kalendari istog resursa se
   osvježe bez reloada. Događaj ide poslije commita, a tok vidi samo član organizacije.
5. ✅ **Ponavljajuće rezervacije** (22.11.2026). Isti termin svake sedmice, N puta (najviše 12). Sve ili ništa u jednoj
   transakciji, a `409` kaže koji datumi su zauzeti. UI: izbor broja sedmica pri rezervaciji.
6. ✅ **QR check-in** (22.11.2026). Rezervacija ima QR kod s potpisanim tokenom. Provider ga skenira ili otvori link i
   rezervacija prelazi u `COMPLETED`. Bez tokena ili za tuđi resurs nema prelaza.
7. ✅ **Kapacitet veći od 1** (22.11.2026). Grupni trening s N mjesta. Plan je bio lock resursa i brojanje,
   a urađeno je drugačije: svaka rezervacija dobija broj mjesta i `EXCLUDE` važi po mjestu (`docs/ODLUKE.md`).
   Dokaz je test sa 20 niti na 5 mjesta (prođe tačno 5). Urađeno u četiri dijela: 7a baza i rezervacija,
   7b kalendar, lista čekanja i serije, 7c UI, 7d e2e i pregled ekrana.
8. ✅ **Kraj faze** (22.11.2026). README, status, ODLUKE. Sve iz plana je stiglo, a ono što je ostalo otvoreno je u Dodacima.

## Faza 11 po koracima

Kriterij "gotovo" je gore (tačka 11): plaćanje u test modu radi do kraja, isti webhook poslan dvaput ne
duplira uplatu, neispravan potpis se odbija. Pravi novac ne ide kroz projekat. Stripe je u testovima
lažan, pa CI ne zavisi od njihovog servera, a pravi test mode se provjerava ručno sa Stripe CLI.

1. **Cijena na resursu.** Kolona u feninzima, 0 je besplatno, API i polje u formi resursa. Besplatan
   resurs radi kao do sada.
2. **Rezervacija čeka uplatu, Stripe Checkout.** Rezervacija plaćenog resursa je `PENDING` s kratkim
   rokom za uplatu, `POST /api/bookings/{id}/pay` vraća adresu Stripe Checkout stranice, uplata ima
   svoju tabelu.
3. **Webhook.** Provjera potpisa, idempotentna obrada (isti događaj dvaput ne radi ništa drugi put),
   uplata potvrđuje rezervaciju. Uplata koja stigne za već isteklu rezervaciju se vraća.
4. **Povrat i istek.** Otkazivanje u roku vraća novac, istekla neplaćena rezervacija oslobađa termin.
5. **UI i kraj faze.** Dugme za plaćanje, povratak sa Checkouta, ručna provjera sa Stripe CLI,
   README, ODLUKE, a šta nije stiglo ide u Dodatke.

## Faza 14 po koracima

Svaki korak je jedna sesija ili manje. Domen ostaje naš: "alat za usluge" je kod nas
`list_resources`, jer imamo resurse (čitaonica, termin konsultacija, sala), a ne usluge.

1. **Odluka: zvanični Anthropic Java SDK ili Spring AI.** Provjeriti da li Spring AI
   podržava naš Spring Boot (4.1) i kako izgleda tool calling u obje varijante. Predlog
   je zvanični SDK (`com.anthropic:anthropic-java`): petlja alata je napisana kod nas pa je
   mogu objasniti liniju po liniju, imamo jednog provajdera, i nema još jednog sloja
   konfiguracije. Spring AI bi dao zamjenu provajdera i manje koda, a košta magiju koju
   na intervjuu moram znati objasniti. Model i verziju SDK-a provjeravamo u dokumentaciji
   kad dođemo do koraka. Odluka ide u `docs/ODLUKE.md`.
2. **Klijent i konfiguracija.** `ANTHROPIC_API_KEY` samo iz varijable okruženja, lažna
   vrijednost u `.env.example`, opis u README-u, na Renderu ga upisujem ručno. Timeout,
   `max_tokens` i najveći broj krugova alata su u konfiguraciji. Bez ključa se aplikacija
   normalno pokreće, a chat vraća `503`. Poziv SDK-a je u jednoj klasi, da je test može
   zamijeniti.
3. **Historija razgovora.** Flyway migracija za `chat_sessions` (korisnik, organizacija,
   vrijeme zadnje poruke) i `chat_messages` (uloga, sadržaj, vrijeme). Sesija pripada
   jednom korisniku i jednoj organizaciji, tuđa sesija se ne može čitati ni nastaviti.
   Modelu ide samo zadnjih N poruka, da razgovor ne raste bez kraja.
4. **Alati za čitanje:** `list_resources`, `check_availability` (resurs, datum, od-do
   lokalno vrijeme) i `get_my_bookings`. Svaki alat zove postojeći servis
   (`ResourceService`, `FreeSlotService`, `BookingService.mine`), bez svoje poslovne
   logike. "Sutra poslije 16h" se pretvara u `Instant` u zoni organizacije, preko `Clock`
   beana, istom logikom kao kalendar, pa DST radi i ovdje. Unit test za svaki alat.
5. **Alati za pisanje i potvrda:** `create_booking` i `cancel_booking` ne mijenjaju ništa,
   nego zapisuju prijedlog uz sesiju, a asistent pita korisnika. Rezervacija nastaje tek
   kad korisnik potvrdi (`POST /api/chat/{sessionId}/confirm`, dugme u widgetu), kroz
   `BookingService.create` s `Idempotency-Key` iz prijedloga, pa dupli klik ne pravi dvije.
   Zašto ne samo pravilo u promptu: model može pogriješiti ili ga tekst korisnika može
   nagovoriti, a server ne može. Ovo je predlog, odlučujemo na početku koraka.
6. **Autorizacija u alatima.** `@PreAuthorize` je na kontrolerima, a alate zove
   `ChatService`, pa se `ResourceAccess` i `BookingAccess` pozivaju eksplicitno. ID
   korisnika uvijek dolazi iz tokena, nikad iz argumenata koje je napisao model, a resurs
   mora biti iz organizacije sesije. Test: chat u organizaciji A traži resurs iz B i ništa
   se ne desi.
7. **Petlja i endpoint.** `POST /api/chat` (poruka i opcionalno `sessionId`):
   `ChatService` šalje historiju i alate, izvršava alate koje model traži i vraća rezultat,
   najviše N krugova. Nema slobodnih termina, nevažeći datum i zauzet termin (`409`) idu
   modelu kao rezultat alata s greškom, pa on korisniku kaže prirodnim jezikom šta da
   pokuša. Timeout ili greška API-ja daje Problem Details s porukom na bosanskom. Rate
   limit po korisniku (Bucket4j iz Faze 8), jer svaki poziv košta.
8. **Integracioni testovi s mockanim LLM klijentom.** Klijent vraća unaprijed zadane
   odgovore (prvo zahtjev za alat, pa tekst). Dokazujemo: bez potvrde nema rezervacije;
   dva chata na isti termin daju jednu rezervaciju i jednu poruku da je zauzeto; druga
   poruka u sesiji šalje modelu i prvu; tuđa organizacija i tuđa sesija ne prolaze;
   timeout daje `503`. Test izolacije organizacija dobija nove rute.
9. **Chat widget (React).** Na stranici za rezervaciju iz Faze 7, po `docs/DIZAJN.md`.
   Stanja učitavanja, greške i praznog razgovora, prijedlog rezervacije s dugmadima
   Potvrdi i Odustani, vrijeme u zoni organizacije, rad tastaturom i na telefonu.
10. **Dokumentacija i ručna provjera.** Sekcija u README-u, `docs/AI_ASISTENT.md`
    (arhitektura, petlja alata, zašto potvrda na serveru, zašto isti servisi, šta nije
    provjereno, jednostavnim riječima za intervju), sekvencijski dijagram u
    `docs/ARHITEKTURA.md`. Par razgovora s pravim ključem zapisujem iskreno: šta je
    radilo, šta nije, koliko krugova i tokena je trebalo.

## Strategija testiranja

- Unit testovi za poslovnu logiku koja nema bazu (računanje termina, prelazi stanja,
  pravilo otkazivanja).
- Integracioni testovi na Testcontainers PostgreSQL, nikad na lokalnu bazu.
- Testovi konkurentnosti (20 niti) i testovi vremena (DST, ponoć, zona različita od
  serverske).
- Sigurnosni testovi: tuđa organizacija, tuđa rezervacija, istekao token, ponovljen
  refresh token, previše pokušaja prijave.
- Frontend: Vitest + Testing Library za komponente, Playwright za glavne tokove.
- Load test (k6) nad lokalnim okruženjem, brojke bilježimo iskreno uz opis mašine.
- JaCoCo izvještaj u CI-ju. U README stavljamo stvarnu pokrivenost, ne ciljanu.

## Tehničke odluke koje ćemo donijeti (i upisati u `docs/ODLUKE.md`)

- Zaštita od duplih rezervacija: `EXCLUDE` constraint (odabrano) vs pesimistično
  zaključavanje (`SELECT ... FOR UPDATE`). Objasnit ćemo oba i zašto jedan pobjeđuje.
- Slobodni termini se računaju na zahtjev, ne spremaju se. Cijena: malo više logike.
  Dobit: nema "mrtvih" redova i promjena pravila odmah važi.
- Email preko `notifications` tabele (outbox), ne direktno iz servisa.
- Skladište slika: Render nema trajni disk, pa idu u S3-kompatibilni servis (npr. Cloudflare R2).
- Refresh tokeni: rotacija s `family_id` i detekcijom ponovne upotrebe. Gdje ih čuva
  klijent (httpOnly kolačić vs memorija) i koji napadi se time zatvaraju.
- Idempotentnost: `Idempotency-Key` na rezervaciji, `processed_webhook_events` na webhooku.
- Rate limiting: u memoriji (jedna instanca) vs u bazi/Redisu (više instanci), i zašto
  biramo prvo.
- Kapacitet > 1: `EXCLUDE` ne može izraziti "do N preklapanja", pa treba drugi mehanizam
  (zaključavanje reda slota ili uslovni `UPDATE` brojača). Uporedit ćemo ih.
- Keširanje slobodnih termina: ne keširamo dok mjerenje ne pokaže da treba, jer pogrešan
  keš znači pogrešan termin.
- Brisanje naloga: anonimizacija umjesto stvarnog brisanja redova, da statistika i
  historija rezervacija ostanu tačne.
- ShedLock: treba li nam dok radi jedna instanca, i šta se desi kad radi dvije.
- Jezik UI-ja: bosanski, struktura spremna za engleski.
- AI asistent: zvanični Anthropic Java SDK vs Spring AI, i potvrda rezervacije na serveru
  vs samo pravilo u promptu.

## Rizici koje provjeravamo rano

- Podržava li Neon `btree_gist` (potreban za `EXCLUDE` na `resource_id` + vremenski raspon).
  Ako ne, plan B je pesimistično zaključavanje.
- Kompatibilnost Testcontainersa, springdoc-a, Bucket4j-a i S3 klijenta s verzijom Spring
  Boota koju koristimo. Verzije NE pretpostavljamo, provjeravamo ih kad dođemo do njih.
- Besplatni planovi (Render, Neon) uspavljuju servis ili bazu, pa prvi zahtjev posjetioca
  može biti spor. Provjeriti aktuelne uslove planova i odlučiti kako to ublažiti.
- Stripe: test mode radi za demo, ali dostupnost naplate za pružaoce iz BiH treba
  provjeriti prije nego što bilo šta tvrdimo u README-u. Stvarni novac ne ide kroz projekat.
- Opseg: plan je velik. Pravilo "ako vrijeme stisne" iznad je tu da jezgro ne zavisi od
  dodataka.
- AI asistent: kompatibilnost SDK-a (ili Spring AI) s našim Spring Bootom i Jacksonom,
  trošak API poziva na live demu (limit i rate limit prije nego što link ide u
  portfolio), i model koji pogrešno shvati datum. Zato alati sami provjeravaju ulaz.

## Definicija "gotovo" za Projekat 2

- [ ] Sve funkcionalnosti iz jezgra rade u produkciji (live URL + demo računi)
- [ ] Barem 80 backend testova, uključujući test konkurentnosti, test prelaska na ljetno
  vrijeme i test izolacije organizacija, plus frontend testovi i bar 3 Playwright toka
- [ ] CI badge u README-u, Swagger UI dostupan, CI skenira zavisnosti i tajne
- [ ] README, `docs/ARHITEKTURA.md` (Mermaid), `docs/ODLUKE.md`, `docs/SIGURNOST.md`,
  `docs/RUNBOOK.md`, screenshotovi
- [ ] k6 rezultati i vraćen backup zapisani s datumom i opisom
- [ ] Nema tajni u repozitoriju (provjereno alatom, ne samo okom)
- [ ] Mogu bez gledanja objasniti: `EXCLUDE` constraint, zašto outbox, refresh token
  rotaciju i detekciju ponovne upotrebe, kako se računaju slobodni termini i DST, šta se
  desi pri istovremenoj rezervaciji, `Idempotency-Key`, zašto webhook mora biti
  idempotentan, kako radi rate limiting

---

# Završna faza: Portfolio sajt

React stranica: o meni, 2 projekta (opis problema, stack, live demo, GitHub),
kontakt, CV. Vlastiti domen (Cloudflare Registrar ili Namecheap), hosting na
Vercel/Netlify/Cloudflare Pages. Projekti idu na poddomene (npr. `slotbook.<domen>`).
Isto pravilo o stilu i neobičnim fontovima kao za SlotBook (vidi CLAUDE.md).
Detaljne korake radimo zajedno kad dođemo do tog dijela.

---

# Dodaci (ideje koje nisu u fazama)

Ovdje zapisujemo sve što nam padne na pamet tokom rada. U Fazi 13 odlučujemo šta ulazi.

- Web push obavijesti / PWA (instalacija na telefon)
- Sinhronizacija s Google Calendarom (dvosmjerna, za razliku od iCal pretplate)
- Ocjene i komentari nakon termina
- SMS podsjetnici
- Grupe resursa (npr. "sve sale na drugom spratu") i traženje slobodnog resursa iz grupe
- Redis za rate limiting i keš kad bi radilo više instanci
- Praćenje troškova i mjerenje potrošnje besplatnih planova
- Sedmična mreža kalendara na desktopu (sada je jedan dan i na telefonu i na desktopu)
- Broj slobodnih termina ispod svakog dana u traci s danima
- Strelice lijevo i desno kroz traku s danima (sada samo Tab)
- Lista otvorenih pozivnica za admina, s povlačenjem pozivnice (sada admin vidi samo potvrdu odmah poslije slanja)
- Promjena uloge člana i uklanjanje člana iz organizacije (backend za to nema endpoint)
- Vlastito polje za sat u 24-satnom obliku (ugrađeno `<input type="time">` prati jezik browsera, pa na engleskom pokazuje AM/PM)
- Kopiranje radnog vremena jednog dana na ostale radne dane
- Toplotna mapa dan x sat za statistiku (sada je samo pet najtraženijih termina), traži sekvencijalnu paletu koje u `docs/DIZAJN.md` nema
- Statistika za proizvoljan period, ne samo po mjesecima (backend prima do 366 dana)
- Godina uz datum izuzetka kad nije tekuća
- ID zahtjeva u poruci frontenda za grešku `500` ("javi nam i navedi broj ..."), backend ga već šalje u tijelu i u `X-Request-Id`
- Manje buke u logu kod sudara: svaki `409` ostavi dvije WARN linije Hibernatea (`23P01`), 1 140 linija u jednom k6 pokretanju
- Metrike na produkciji: registry koji šalje brojače van (npr. OTLP prema besplatnom Grafana Cloudu), jer je `/actuator/metrics` na Renderu namjerno zatvoren

Otvoreno iz Faze 10:

- Da mjesto nije veće od kapaciteta čuva samo Java pod lockom resursa. Baza bi to mogla čuvati tabelom `resource_seats (resource_id, seat)` sa složenim FK s rezervacije. Cijena: promjena kapaciteta dodaje i briše redove, a staro mjesto u historiji treba riješiti.
- Kad se u grupnom terminu istovremeno oslobode dva mjesta, drugo se nudi s liste čekanja minutu kasnije (jedna ponuda po terminu po prolazu posla).
- Raspored providera ne pokazuje broj mjesta, samo više klijenata u istom terminu. "Popunjeno" u kalendaru pokrivaju Vitest i backend testovi, e2e ne.
- QR kod nije skeniran stvarnom kamerom telefona (čeka frontend na Vercelu).
- Otkazivanje cijele serije odjednom i jedan email za cijelu seriju (sada se otkazuje sedmica po sedmica, a serija od 12 sedmica pošalje 12 emailova).
- SSE veze su u memoriji jedne instance. Za više instanci treba Postgres `LISTEN/NOTIFY` ili Redis.

Otvoreno iz Faze 9 (ručni koraci i dug):

- Sentry je povezan (`SENTRY_DSN` na Renderu od 22.11.2026), ali prvi pravi događaj s produkcije još nije stigao, pa slanje tamo nije viđeno. Region organizacije nisam potvrdio.
- k6 je mjeren samo na laptopu, ne na Renderu i Neon-u.

Otvoreno iz Faze 8 (dug, za pregled u Fazi 13):

- Access token važi 15 minuta i ne opoziva se, ni poslije brisanja naloga ni poslije nove lozinke.
- Reset lozinke za postojeći email je sporiji 6 do 15 ms (MockMvc), granica od tri maila na sat se broji bez zaključavanja.
- Ništa od zaglavlja, HSTS-a, CORS-a i IP adrese iz `CF-Connecting-IP` nije provjereno na Renderu.
- Akcije u workflowima su na glavnoj verziji (`@v7`), ne na SHA commita.
- Nema alarma za zaključane prijave, `429` i ponovljen refresh token, vide se samo u logu.
- Kod ne provjerava dužinu `JWT_SECRET` pri startu.
- Broj CodeQL nalaza iz prve analize nije upisan u `docs/SIGURNOST.md`.

Otvoreno iz Faze 7 (dug, za pregled u Fazi 13):

- Raspored providera, admin ekrani (članovi, pozivnice, resursi, radno vrijeme) i statistika s CSV-om su u browseru provjereni samo s lažnim backendom. S pravim backendom ide samo glavni tok iz Playwright testa.
- CI ne pokreće Playwright (Vitest, Oxlint i build pokreće od Faze 8).
- Kad backend iz Playwright testa ne starta, kontejneri iz `frontend/e2e/compose.yml` ostanu upaljeni (gasi ih `docker compose -f e2e/compose.yml down`).
- U koraku 11 je jednom, odmah poslije mutacija, palo 6 Vitest testova na fajlovima koji su bili vraćeni bajt po bajt. Uzrok nije nađen.

Otvoreno iz Faze 6 (dug, za pregled u Fazi 13):

- Radno vrijeme i deaktivacija resursa nemaju historiju, pa se popunjenost prošlog perioda računa po sadašnjim pravilima i može preći 100 %.
- CSV nije ručno otvoren u Excelu: separator `;`, BOM i apostrof ispred formule su po dokumentaciji, ne provjereni.
- Upiti statistike su mjereni samo lokalno, sve u memoriji. Na Neon-u nisu mjereni, kao ni cijeli zahtjev (2-3 upita po resursu za ponuđene minute) i trošak upisa zbog novog indeksa.
- Za `400` uz `Accept: text/csv` provjeren je samo status, ne i tijelo greške.
- Indeks s `INCLUDE (status, end_time)` za godišnji period nije proban.

Otvoreno iz Faze 5 (dug, za pregled u Fazi 13):

- Rate limit za reset lozinke, novi link za potvrdu emaila i pozivnice nema, pa se nekome može zatrpati inbox. Ide u Fazu 8.
- Access token izdat prije reseta lozinke važi još do 15 minuta.
- Obavijest koja baci grešku koja nije `MailException` (bug) ostaje prva u redu i blokira ostale u tom krugu posla.
- Mail otkazivanja ne nosi `.ics` s `METHOD:CANCEL`, pa termin ostaje u kalendaru korisnika. Prikaz `.ics` u Gmailu i Outlooku nije ručno proban.
- Razlika u vremenu odgovora za reset lozinke s postojećim i nepostojećim emailom nije mjerena.

Otvoreno iz Faze 4 (nije ideja nego dug, za pregled u Fazi 13):

- `S3StorageServiceTest` je jednom pao usred cijele suite (`The target server failed to respond`), a sam prolazi.
- Handler za `ObjectOptimisticLockingFailureException` (`409`) nije provjeren kroz HTTP, samo `@Version` u servisu.
- Grana `23505` -> `422` (dva različita termina s istim `Idempotency-Key` u istom trenutku) nema test.

Otvoreno iz Faze 10:

- Serija rezervacija: otkazivanje cijele serije odjednom i jedan email za seriju umjesto po jednog za svaku sedmicu.
- SSE radi samo s jednom instancom backenda (veze su u memoriji). Na Renderu nije provjereno koliko dugo drži otvorenu vezu.
