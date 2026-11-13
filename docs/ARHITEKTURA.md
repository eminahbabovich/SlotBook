# Arhitektura

Opis onoga što postoji sada (Faza 3 u izradi). Rezervacije, obavijesti i frontend još ne postoje, pa ih ovdje nema.

## Paketi i slojevi

Backend je jedna Spring Boot aplikacija. Paketi su po funkcionalnosti, ne po sloju: `auth`, `user`, `organization`, `resource`, `availability`, a uz njih `error` (Problem Details), `config` (security, sat, JWT, Swagger), `paging` (oblik stranice) i `status`.

Unutar paketa je uvijek isti red: kontroler prima zahtjev i provjerava pristup, servis drži pravila i transakciju, repozitorij priča s bazom. Kontroler vraća samo DTO (`record`), nikad entitet. Repozitorij organizacija je vidljiv samo u paketu `organization`, pa paket `resource` do organizacije dolazi preko `OrganizationService`.

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

Rute s organizacijom u putanji (`/api/organizations/{id}/...`) provjerava `OrganizationAccess`. Rute bez nje (`/api/resources/{resourceId}`) provjerava `ResourceAccess`, koji organizaciju uzima iz resursa.

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
```

Resurs ne pokazuje na `users` direktno, nego na par (organizacija, korisnik) u `organization_members`. Tako provider ne može biti neko izvan organizacije resursa, ni kad je Java kod pogrešan. Sva vremena su `TIMESTAMPTZ`, a ID-jevi UUID. Zašto je tako, piše u [ODLUKE.md](ODLUKE.md).

## Slobodni termini

Slobodni termini nisu tabela. `GET /api/resources/{resourceId}/slots?from=&to=` ide kroz `SlotService`, koji iz baze učita resurs sa zonom organizacije, sedmična pravila i izuzetke koji dodiruju traženi period. Onda sve to preda klasi `SlotCalculator`, koja nema ni Spring ni bazu i samo računa. Trenutno vrijeme dolazi iz `Clock` beana, pa se termini koji su već počeli ne nude.

## Šta dolazi

U ostatku Faze 3 dolazi slika resursa kroz `StorageService`. U Fazi 4 dolazi `bookings` s `EXCLUDE` constraintom, i tada se od slobodnih termina oduzimaju i rezervacije.
