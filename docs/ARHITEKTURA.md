# Arhitektura

Opis onoga što postoji sada (Faze 1 do 4). Obavijesti i frontend još ne postoje, pa ih ovdje nema.

## Paketi i slojevi

Backend je jedna Spring Boot aplikacija. Paketi su po funkcionalnosti, ne po sloju: `auth`, `user`, `organization`, `resource`, `availability`, `booking`, `notification`, a uz njih `error` (Problem Details), `config` (security, sat, JWT, Swagger), `paging` (oblik stranice), `storage` (gdje se čuvaju slike) i `status`.

Unutar paketa je uvijek isti red: kontroler prima zahtjev i provjerava pristup, servis drži pravila i transakciju, repozitorij priča s bazom. Kontroler vraća samo DTO (`record`), nikad entitet. Repozitorij organizacija je vidljiv samo u paketu `organization`, pa paket `resource` do organizacije dolazi preko `OrganizationService`. Isto važi za rezervacije: `BookingRepository` vidi samo paket `booking`.

Zavisnosti idu u jednom smjeru: `booking` zna za `availability` (koji termini se nude), a `availability` ne zna za rezervacije. Zato je `GET /api/resources/{id}/slots` u paketu `booking`: `FreeSlotService` uzme ponuđene termine iz `SlotService` i izbaci zauzete.

## Tok jednog zahtjeva

Primjer: trener mijenja svoj resurs.

```mermaid
sequenceDiagram
    participant K as Klijent
    participant F as Security filter chain
    participant C as ResourceController
    participant A as ResourceAccess
    participant S as ResourceService
    participant DB as PostgreSQL

    K->>F: PUT /api/organizations/{id}/resources/{resourceId} + Bearer token
    F->>F: provjeri potpis i rok JWT-a (bez sesije)
    F->>C: zahtjev s JwtAuthenticationToken
    C->>C: @Valid nad tijelom (400 ako ne valja)
    C->>A: @PreAuthorize canManage(resourceId)
    A->>DB: resurs, pa uloga u njegovoj organizaciji
    A-->>C: true ili 403
    C->>S: update(id, resourceId, ...)
    S->>DB: resurs po paru (resourceId, id), inače 404
    S-->>C: ResourceResponse
    C-->>K: 200 JSON
```

Token nosi samo ID korisnika. Uloga se čita iz baze kod svakog zahtjeva, pa oduzeta uloga važi odmah. Svaka greška, i ona iz filtera, prolazi kroz `GlobalExceptionHandler` i vraća se kao `application/problem+json`.

Rute s organizacijom u putanji (`/api/organizations/{id}/...`) provjerava `OrganizationAccess`. Rute bez nje (`/api/resources/{resourceId}`) provjerava `ResourceAccess`, koji organizaciju uzima iz resursa, a rute `/api/bookings/{bookingId}/...` provjerava `BookingAccess`, koji resurs uzima iz rezervacije.

## Tok rezervacije

`POST /api/resources/{resourceId}/bookings` je mjesto gdje se sudaraju istovremeni zahtjevi, pa je redoslijed bitan.

```mermaid
sequenceDiagram
    participant K as Klijent
    participant C as BookingController
    participant S as BookingService
    participant SL as SlotService
    participant DB as PostgreSQL

    K->>C: POST {startTime} + Idempotency-Key
    C->>C: @PreAuthorize canView(resourceId)
    C->>S: create(resourceId, klijent, start, ključ)
    S->>DB: SELECT resurs FOR UPDATE (drugi zahtjev za isti resurs čeka)
    S->>DB: istekle PENDING na ovom resursu u EXPIRED (posao možda kasni)
    S->>DB: ima li već rezervacija s ovim ključem? ako ima, vrati nju (201)
    S->>SL: offered(resourceId, start)
    SL-->>S: termin po pravilima ili prazno (400)
    S->>DB: INSERT bookings (EXCLUDE: 23P01 ako je zauzeto, pa 409)
    S->>DB: INSERT booking_events (nastanak)
    S-->>K: 201 BookingResponse
```

Preklapanje čuva `EXCLUDE` constraint u bazi, a lock resursa postoji da drugi zahtjev dobije čist `409` umjesto deadlocka. Promjene statusa (`/confirm`, `/reject`, `/cancel`, `/check-in`, `/no-show`) idu kroz `Booking.prijedi`, koji zna tabelu dozvoljenih prelaza, a istovremenu izmjenu iste rezervacije hvata `@Version`. Zašto je tako, piše u [ODLUKE.md](ODLUKE.md).

## Model podataka

```mermaid
erDiagram
    users ||--o{ refresh_tokens : "ima"
    users ||--o{ organization_members : "član je"
    organizations ||--o{ organization_members : "ima članove"
    organizations ||--o{ organization_invites : "šalje"
    organization_members ||--o{ resources : "vodi (provider)"
    resources ||--o{ availability_rules : "radno vrijeme"
    resources ||--o{ time_off : "izuzeci"
    resources ||--o{ bookings : "rezervacije"
    users ||--o{ bookings : "klijent"
    bookings ||--o{ booking_events : "historija"
    users ||--o{ booking_events : "promijenio"
    users ||--o{ notifications : "prima"
    bookings ||--o{ notifications : "o čemu"

    users {
        uuid id PK
        varchar email UK "malim slovima, CHECK"
        varchar password_hash
        varchar display_name
        timestamptz created_at
    }
    refresh_tokens {
        uuid id PK
        uuid user_id FK
        uuid family_id "tokeni jedne prijave"
        varchar token_hash UK "SHA-256"
        timestamptz expires_at
        timestamptz revoked_at
        uuid replaced_by FK
    }
    organizations {
        uuid id PK
        varchar name
        varchar slug UK "CHECK formata"
        varchar timezone "IANA zona"
        timestamptz created_at
    }
    organization_members {
        uuid organization_id PK
        uuid user_id PK
        varchar role "ADMIN, PROVIDER, CLIENT"
        timestamptz joined_at
    }
    organization_invites {
        uuid id PK
        uuid organization_id FK
        varchar email
        varchar role
        varchar token_hash UK
        timestamptz expires_at
        timestamptz accepted_at "jedna otvorena po emailu"
    }
    resources {
        uuid id PK
        uuid organization_id FK
        uuid provider_id FK "(organization_id, provider_id) u organization_members"
        varchar name
        varchar description
        int slot_minutes "5 do 480"
        boolean active
        timestamptz created_at
        varchar image_key "ključ u skladištu, ne slika"
        boolean auto_confirm "false: rezervacija čeka potvrdu"
        int cancel_notice_hours "rok za otkazivanje, 0 do 168"
    }
    availability_rules {
        uuid id PK
        uuid resource_id FK
        int day_of_week "1 ponedjeljak do 7 nedjelja"
        time start_time "lokalno u zoni organizacije"
        time end_time "EXCLUDE: bez preklapanja istog dana"
    }
    time_off {
        uuid id PK
        uuid resource_id FK
        timestamptz starts_at "trenutak, ne lokalno vrijeme"
        timestamptz ends_at "CHECK: poslije početka"
        varchar reason "samo za admina i providera"
        timestamptz created_at
    }
    bookings {
        uuid id PK
        uuid resource_id FK "EXCLUDE: bez preklapanja aktivnih"
        uuid client_id FK
        timestamptz start_time
        timestamptz end_time "CHECK: poslije početka"
        varchar status "PENDING i CONFIRMED drže termin"
        timestamptz created_at
        varchar idempotency_key "UNIQUE s client_id"
        bigint version "@Version"
    }
    notifications {
        uuid id PK
        uuid user_id FK
        varchar type "BOOKING_CONFIRMED..."
        uuid booking_id FK
        varchar status "PENDING, SENT, FAILED"
        int attempts
        timestamptz next_attempt_at
        timestamptz created_at
        timestamptz sent_at
        varchar subject "tekst nastaje s obavijesti"
        text body
        varchar last_error
    }
    booking_events {
        uuid id PK
        uuid booking_id FK
        varchar from_status "prazno kod nastanka"
        varchar to_status
        uuid actor_id FK "prazno kad je promjena automatska"
        timestamptz created_at
    }
```

Resurs ne pokazuje na `users` direktno, nego na par (organizacija, korisnik) u `organization_members`. Tako provider ne može biti neko izvan organizacije resursa, ni kad je Java kod pogrešan. Sva vremena su `TIMESTAMPTZ`, a ID-jevi UUID. Zašto je tako, piše u [ODLUKE.md](ODLUKE.md).

## Slobodni termini

Slobodni termini nisu tabela. `GET /api/resources/{resourceId}/slots?from=&to=` ide kroz `FreeSlotService`, koji prvo pita `SlotService`, koji iz baze učita resurs sa zonom organizacije, sedmična pravila i izuzetke koji dodiruju traženi period. Onda sve to preda klasi `SlotCalculator`, koja nema ni Spring ni bazu i samo računa. Trenutno vrijeme dolazi iz `Clock` beana, pa se termini koji su već počeli ne nude. Na kraju `FreeSlotService` izbaci termine koje drži `PENDING` ili `CONFIRMED` rezervacija.

## Slike i šta dolazi

Slika resursa ide kroz interface `StorageService`. Implementacije su `LocalStorageService` (folder na disku) i `S3StorageService` (S3-kompatibilno skladište, Cloudflare R2 na produkciji), a bira se preko `slotbook.storage.type`. Pozadinski posao `BookingJob` (`@Scheduled`, svake minute) prebacuje istekle `PENDING` rezervacije u `EXPIRED` i zatvara potvrđene dan poslije kraja. Oba prelaza su po jedna SQL naredba koja mijenja status i piše historiju bez aktera. Obavijesti idu kroz tabelu `notifications` (outbox): `BookingService` je upiše preko `NotificationService` u istoj transakciji kao promjenu rezervacije, a `NotificationJob` ih svakih 30 sekundi šalje kroz `NotificationSender`, jednu po jednu, svaku u svojoj transakciji (`FOR UPDATE SKIP LOCKED`). Tekst emaila (`subject`, `body`) se upiše kad obavijest nastane, pa pošiljalac ne zna ništa o rezervacijama. Paket `booking` zna za `notification`, a obrnuto ne, zato obavijest drži samo `booking_id`.
