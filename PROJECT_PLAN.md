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

# PROJEKAT 2: SlotBook (faze 1-13)

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
  notified_at, created_at)
- `notifications` (id, user_id, type, payload JSONB, status, attempts, next_attempt_at,
  created_at, sent_at)
- `payments` (id, booking_id, provider_ref, amount, currency, status, created_at)
- `processed_webhook_events` (event_id PK, received_at)
- `audit_log` (id, organization_id, actor_id, action, entity_type, entity_id, details JSONB,
  created_at)
- `shedlock` (ako odlučimo da ga koristimo)

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
| POST/DELETE | /api/resources/{id}/waitlist | lista čekanja |
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
| 2 | Auth i organizacije | Korisnici, BCrypt, JWT + **refresh tokeni** (rotacija + detekcija ponovne upotrebe preko `family_id`), organizacije, uloge po organizaciji, pozivnice, `@PreAuthorize` s vlastitim provjerama članstva | Method security, `PermissionEvaluator` / bean u SpEL, refresh token tok, autorizacija koja ovisi o resursu | u izradi |
| 3 | Resursi i dostupnost | CRUD resursa, sedmična pravila, `time_off`, **algoritam za slobodne termine**, upload slike kroz `StorageService` (lokalno + S3-kompatibilno), paginacija | `java.time` (`Instant`, `ZonedDateTime`, DST), `TIMESTAMPTZ`, interface + dvije implementacije, `MultipartFile` | ⏳ nije počelo |
| 4 | Rezervacije i konkurentnost | Kreiranje rezervacije, **`EXCLUDE` constraint**, stanja i prelazi, `booking_events`, `Idempotency-Key`, pravilo otkazivanja, `@Version`, **test sa 20 niti** | PostgreSQL range tipovi, transakcije i izolacija, optimistično vs pesimistično zaključavanje, `409 Conflict`, state machine, idempotentnost | ⏳ nije počelo |
| 5 | Pozadinski poslovi i obavijesti | `@Scheduled` istek `PENDING` i zatvaranje završenih, podsjetnici, `notifications` tabela (outbox), slanje emaila, `@TransactionalEventListener`, ponovni pokušaj s backoffom, verifikacija emaila, reset lozinke, slanje pozivnica, `.ics` prilog u potvrdi | Scheduling, zašto se email ne šalje unutar iste transakcije, `AFTER_COMMIT`, idempotentnost, ShedLock, sigurni tokeni za reset | ⏳ nije počelo |
| 6 | Statistika | Agregatni upiti (`GROUP BY`, popunjenost, otkazivanja i no-show po periodu, najtraženiji termini dan x sat), endpointi za providera i admina, CSV izvoz | JPQL/native upiti, projekcije u DTO, indeksi i `EXPLAIN` | ⏳ nije počelo |
| 7 | Frontend (jezgro) | **Prvo biramo vizuelni smjer i fontove** (vidi CLAUDE.md). Prijava, izbor organizacije, kalendar slobodnih termina, tok rezervacije, moje rezervacije, kalendar providera, admin (članovi, resursi, radno vrijeme), dashboard sa grafovima, vrijeme u zoni organizacije, pristupačnost, mobilni prikaz, Vitest + Playwright za glavni tok | React kalendar biblioteka, zaštićene rute po ulozi, obrada `409`, refresh token u klijentu (jedan refresh za više paralelnih zahtjeva), e2e testiranje | ⏳ nije počelo |
| 8 | Sigurnost i GDPR | Rate limiting (Bucket4j), zaključavanje naloga, sigurnosna zaglavlja, CORS, `audit_log`, izvoz i brisanje naloga, zaštita od enumeracije emailova, OWASP Top 10 prolaz, CodeQL + skeniranje zavisnosti + gitleaks u CI-ju, `docs/SIGURNOST.md`, **test koji prođe kroz sve endpointe s tokenom iz tuđe organizacije** | Napadi koji se stvarno dešavaju (IDOR, brute force, enumeracija), rate limiting algoritmi, anonimizacija, šta je tajna a šta nije | ⏳ nije počelo |
| 9 | Observability i performanse | Actuator (health, readiness), Micrometer metrike (rezervacije, konflikti, neuspjeli mailovi), JSON logovi s ID-jem zahtjeva (MDC), praćenje grešaka (Sentry free), indeksi + `EXPLAIN`, traženje N+1 upita, **k6 load test** s stvarnim brojkama, `RUNBOOK.md` s **provjerenim** backup/restore postupkom | Metrike vs logovi, kako se nađe spor upit, connection pool, šta load test zaista pokazuje | ⏳ nije počelo |
| 10 | Napredne funkcije | Redom po vrijednosti: lista čekanja, iCal pretplata, SSE, ponavljajuće rezervacije, QR check-in, resursi s kapacitetom > 1. Svaka s vlastitim testom i UI-jem | SSE i `SseEmitter`, pravila ponavljanja, delimični konflikti u seriji, drugačiji mehanizam zaštite kad `EXCLUDE` ne vrijedi | ⏳ nije počelo |
| 11 | Plaćanje (Stripe, test mode) | Cijena na resursu, Checkout, `PENDING` do uplate, webhook s provjerom potpisa, **idempotentna obrada webhooka**, povrat pri otkazivanju po pravilu, istek neplaćenih | Webhookovi, zašto se isti događaj može stići dvaput, razlika između plaćanja i potvrde, rad s eksternim API-jem u testovima | ⏳ nije počelo |
| 12 | Deploy i prezentacija | Deploy (Render/Neon/Vercel + R2 za slike), demo seed i demo računi (profil `demo`, noćni reset demo podataka), uptime monitor, README, `docs/`, screenshotovi i kratki GIF toka, CI badge, poddomen | Env varijable u produkciji, profili (`demo`), skladište slika, health check, hladni start besplatnih planova | ⏳ nije počelo |
| 13 | Završni pregled i dodaci | Pregled cijelog koda (`/code-review`), čišćenje mrtvog koda, prolaz kroz README i `docs/` da sve što piše stvarno radi, engleski prijevod UI-ja, i **lista dodataka koje smo usput zapisali**: ovdje odlučujemo šta još ulazi | Kako se čita tuđi (svoj stari) kod, šta bih prvo mijenjao | ⏳ nije počelo |

Pravilo iz P1 ostaje: ne čekati kraj s deployem. Zato u Fazi 1 uradimo "hodajući kostur"
(prazan endpoint na live adresi), a u Fazi 12 samo punimo ono što već radi.

**Ako vrijeme stisne:** faze 1-9 i 12 su jezgro. Ako treba birati, Faza 12 se radi prije
faza 10 i 11, jer je live projekat s čvrstim jezgrom vrijedniji od nedovršenih dodataka.
Unutar faze 10 redoslijed je već po vrijednosti, pa se ono s kraja liste može izostaviti.

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
