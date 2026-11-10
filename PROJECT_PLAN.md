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

# PROJEKAT 2: SlotBook (faze 1-8)

**Problem:** zajednički resursi (učionice za učenje, prostor za sastanke, termini
konsultacija, teretana) rezerviraju se preko poruka i tabela, uz preklapanja i zabune.
Aplikacija omogućava rezervaciju termina bez preklapanja, s ulogama i potvrdom.

**Zašto je različit od P1:** vrijeme i konflikti (preklapanje termina), konkurentnost
(dva korisnika rezerviraju isti termin), uloge, zakazani poslovi, upload slika,
admin panel. Pokriva ono što RoomSplit ne pokriva.

## Funkcionalnosti (MVP)

1. Organizacije (multi-tenant): svaka ima svoje resurse, osoblje i klijente i svoju
   vremensku zonu (npr. teretana, salon, fakultetska biblioteka).
2. Uloge po organizaciji: `ADMIN`, `PROVIDER` (vlasnik resursa), `CLIENT`. Isti korisnik
   može biti `PROVIDER` u jednoj organizaciji, a `CLIENT` u drugoj.
3. Resursi (prostor ili osoba): trajanje termina, minimalni rok za otkazivanje,
   automatska ili ručna potvrda, slika.
4. Dostupnost: sedmično radno vrijeme + izuzeci (godišnji, praznici).
5. Kalendar slobodnih termina. Termini se računaju iz pravila, ne spremaju se unaprijed.
6. Rezervacija bez preklapanja, čak i kad dva zahtjeva stignu istovremeno.
7. Životni ciklus rezervacije: `PENDING` -> `CONFIRMED` -> `COMPLETED`, plus `CANCELLED`
   i `EXPIRED`. Otkazivanje s pravilom (npr. ne manje od 2h prije termina).
8. Pozadinski poslovi (`@Scheduled`): istek neplaćenih/nepotvrđenih rezervacija, podsjetnici.
9. Email obavijesti preko tabele `notifications` (pouzdano slanje, ponovni pokušaj).
10. Upload slike resursa (S3-kompatibilno skladište).
11. Statistika za providera i admina (broj rezervacija, popunjenost, otkazivanja).
12. Demo mod: seed podaci i demo računi, da posjetilac portfolija odmah vidi cijeli tok.

## Model podataka (početna verzija, doradit ćemo uz Claude-a)

- `users` (id, email UNIQUE, password_hash, display_name, created_at)
- `refresh_tokens` (id, user_id, token_hash, expires_at, revoked_at)
- `organizations` (id, name, slug UNIQUE, timezone, created_at)
- `organization_members` (organization_id, user_id, role, joined_at) - PK (organization_id, user_id)
- `resources` (id, organization_id, provider_id, name, description, image_key,
  slot_minutes, min_notice_hours, auto_confirm, active)
- `availability_rules` (id, resource_id, day_of_week, start_time TIME, end_time TIME)
- `time_off` (id, resource_id, starts_at TIMESTAMPTZ, ends_at TIMESTAMPTZ, reason)
- `bookings` (id, resource_id, client_id, start_time TIMESTAMPTZ, end_time TIMESTAMPTZ,
  status, note, expires_at, version, created_at, cancelled_at, cancel_reason)
- `notifications` (id, user_id, type, payload, status, attempts, created_at, sent_at)

Invarijante:
- Dvije aktivne rezervacije (`PENDING`, `CONFIRMED`) istog resursa se ne smiju preklapati.
  Ovo čuva baza (`EXCLUDE` constraint), ne samo Java kod.
- Rezervacija mora cijela stati u radno vrijeme resursa i ne smije pasti u `time_off`.
- Vrijeme se u bazi čuva kao `TIMESTAMPTZ` (UTC), a radno vrijeme kao lokalno vrijeme
  u zoni organizacije. Zato ljetno/zimsko računanje vremena mora raditi ispravno.

## API (skica)

| Metoda | Putanja | Opis |
|---|---|---|
| POST | /api/auth/register, /login, /refresh, /logout | prijava s refresh tokenom |
| POST | /api/organizations | nova organizacija (kreator postaje `ADMIN`) |
| GET | /api/organizations/{slug} | javni profil organizacije |
| POST | /api/organizations/{id}/members | dodaj člana s ulogom (`ADMIN`) |
| POST/GET/PUT/DELETE | /api/organizations/{id}/resources | CRUD resursa (`ADMIN`, `PROVIDER`) |
| PUT | /api/resources/{id}/availability | sedmično radno vrijeme |
| POST | /api/resources/{id}/time-off | izuzetak (godišnji, praznik) |
| POST | /api/resources/{id}/image | upload slike |
| GET | /api/resources/{id}/slots?from=&to= | slobodni termini |
| POST | /api/resources/{id}/bookings | nova rezervacija (`409` ako je zauzeto) |
| GET | /api/bookings/mine | moje rezervacije |
| POST | /api/bookings/{id}/confirm, /cancel | potvrda / otkazivanje |
| GET | /api/organizations/{id}/stats | statistika (`ADMIN`, `PROVIDER`) |

## Faze

| Faza | Naziv | Fokus | Šta ja učim | Status |
|---|---|---|---|---|
| 1 | Temelji i infrastruktura | Docker Compose (Postgres + Mailpit), Flyway, **Testcontainers**, **GitHub Actions**, springdoc, prvi endpoint + test, **provjera `btree_gist` na Neon-u** | Testcontainers, CI pipeline, Compose, zašto testovi ne smiju ovisiti o mojoj lokalnoj bazi | ⏳ nije počelo |
| 2 | Auth i organizacije | Korisnici, JWT + **refresh tokeni** (rotacija), organizacije, uloge po organizaciji, `@PreAuthorize` s vlastitim provjerama članstva | Method security, `PermissionEvaluator` / bean u SpEL, refresh token tok, autorizacija koja ovisi o resursu | ⏳ nije počelo |
| 3 | Resursi i dostupnost | CRUD resursa, sedmična pravila, `time_off`, **algoritam za slobodne termine**, upload slike kroz `StorageService` (lokalno + S3-kompatibilno) | `java.time` (`Instant`, `ZonedDateTime`, DST), `TIMESTAMPTZ`, interface + dvije implementacije, `MultipartFile` | ⏳ nije počelo |
| 4 | Rezervacije i konkurentnost | Kreiranje rezervacije, **`EXCLUDE` constraint**, stanja i prelazi, pravilo otkazivanja, `@Version`, **test sa 20 niti** | PostgreSQL range tipovi, transakcije i izolacija, optimistično vs pesimistično zaključavanje, `409 Conflict`, state machine | ⏳ nije počelo |
| 5 | Pozadinski poslovi i obavijesti | `@Scheduled` istek `PENDING`, podsjetnici, `notifications` tabela (outbox), slanje emaila, `@TransactionalEventListener`, ponovni pokušaj | Scheduling, zašto se email ne šalje unutar iste transakcije, `AFTER_COMMIT`, idempotentnost, (opcionalno) ShedLock | ⏳ nije počelo |
| 6 | Statistika | Agregatni upiti (`GROUP BY`, popunjenost, otkazivanja po periodu), endpointi za providera i admina | JPQL/native upiti, projekcije u DTO, indeksi i `EXPLAIN` | ⏳ nije počelo |
| 7 | Frontend (lokalno) | Biranje termina (klijent), kalendar providera, admin dashboard sa grafovima, prikaz vremena u zoni organizacije. **Prvo biramo vizuelni smjer i fontove** (vidi CLAUDE.md) | React kalendar biblioteka, zaštićene rute po ulozi, obrada `409`, refresh token u klijentu | ⏳ nije počelo |
| 8 | Deploy i prezentacija | Deploy (Render/Neon/Vercel + R2 za slike), demo seed i demo računi, README, `docs/`, screenshotovi, poddomen | Env varijable u produkciji, profili (`demo`), skladište slika, health check | ⏳ nije počelo |

Pravilo iz P1 ostaje: ne čekati kraj s deployem. Zato u Fazi 1 uradimo "hodajući kostur"
(prazan endpoint na live adresi), a u Fazi 8 samo punimo ono što već radi.

## Kriterij "gotovo" po fazi

1. CI je zelen na GitHubu, testovi se vrte na Testcontainers Postgresu, `btree_gist`
   potvrđen na Neon-u.
2. Refresh tok radi, korisnik iz organizacije A ne može dotaknuti podatke organizacije B
   (postoji test koji to dokazuje).
3. Slobodni termini su tačni preko prelaska na ljetno vrijeme (test), slika se
   uploaduje i vraća kroz API.
4. Test pokrene 20 niti na isti termin i prođe tačno jedna rezervacija, ostale dobiju `409`.
5. Istekla `PENDING` rezervacija prelazi u `EXPIRED`, a email se šalje i nakon greške
   providera (ponovni pokušaj), bez gubitka rezervacije.
6. Statistika se poklapa s ručno izračunatim brojkama iz testnih podataka.
7. Cijeli tok (prijava, biranje termina, rezervacija, otkazivanje) radi u UI-ju, a
   konflikt se prikaže prijateljskom porukom.
8. Live URL radi, demo računi rade, README i `docs/` su ažurni.

## Tehničke odluke koje ćemo donijeti (i upisati u `docs/ODLUKE.md`)

- Zaštita od duplih rezervacija: `EXCLUDE` constraint (odabrano) vs pesimistično
  zaključavanje (`SELECT ... FOR UPDATE`). Objasnit ćemo oba i zašto jedan pobjeđuje.
- Slobodni termini se računaju na zahtjev, ne spremaju se. Cijena: malo više logike.
  Dobit: nema "mrtvih" redova i promjena pravila odmah važi.
- Email preko `notifications` tabele (outbox), ne direktno iz servisa.
- Skladište slika: Render nema trajni disk, pa idu u S3-kompatibilni servis (npr. Cloudflare R2).

## Rizici koje provjeravamo rano

- Podržava li Neon `btree_gist` (potreban za `EXCLUDE` na `resource_id` + vremenski raspon).
  Ako ne, plan B je pesimistično zaključavanje.
- Kompatibilnost Testcontainersa, springdoc-a i S3 klijenta s verzijom Spring Boota koju
  koristimo. Verzije NE pretpostavljamo, provjeravamo ih u Fazi 1.

## Definicija "gotovo" za Projekat 2

- [ ] Sve MVP funkcionalnosti rade u produkciji (live URL + demo računi)
- [ ] Barem 40 testova, uključujući test konkurentnosti i test prelaska na ljetno vrijeme
- [ ] CI badge u README-u, Swagger UI dostupan
- [ ] README, `docs/ARHITEKTURA.md` (Mermaid), `docs/ODLUKE.md`, screenshotovi
- [ ] Nema tajni u repozitoriju (provjereno)
- [ ] Mogu bez gledanja objasniti: `EXCLUDE` constraint, zašto outbox, refresh token
  rotaciju, kako se računaju slobodni termini i šta se desi pri istovremenoj rezervaciji

---

# Završna faza: Portfolio sajt

React stranica: o meni, 2 projekta (opis problema, stack, live demo, GitHub),
kontakt, CV. Vlastiti domen (Cloudflare Registrar ili Namecheap), hosting na
Vercel/Netlify/Cloudflare Pages. Projekti idu na poddomene (npr. `slotbook.<domen>`).
Isto pravilo o stilu i neobičnim fontovima kao za SlotBook (vidi CLAUDE.md).
Detaljne korake radimo zajedno kad dođemo do tog dijela.

---

# Bonus ideje ako ostane vremena

- Live ažuriranje kalendara preko SSE (kad neko rezervira, drugima nestane termin)
- Ponavljajuće rezervacije (svake sedmice)
- Lista čekanja kad je termin zauzet
- Rate limiting na prijavu i rezervacije (Bucket4j)
