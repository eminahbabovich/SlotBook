# Runbook

Šta uraditi kad nešto ne radi, i kako se pravi i vraća backup. Sve komande su za PowerShell na Windowsu, s Dockerom. Lozinke se nikad ne pišu u komandu. `psql` i `pg_dump` ih traže sami, pa ne ostaju u historiji terminala.

## Da li aplikacija radi

`/actuator/health/liveness` kaže da je proces živ. `/actuator/health/readiness` kaže da prima zahtjeve i da baza odgovara. Render gleda readiness (Settings > Health Check Path: `/actuator/health/readiness`).

```powershell
curl.exe https://slotbook-dxvs.onrender.com/actuator/health/readiness
```

`{"status":"UP"}` je dobro. Provjereno na produkciji 22.11.2026. poslije deploya `be963f0`: readiness i liveness vraćaju `UP`, `/api/status` ima zaglavlje `x-request-id`, a `/actuator/metrics` bez prijave vraća `401`. `503` s `"status":"DOWN"` znači da baza ne odgovara. Tada prvo pogledati Neon konzolu (da li je projekat suspendovan ili prekoračio limit), pa log na Renderu. Prvi zahtjev poslije spavanja traje i do minute. To je hladni start besplatnog plana, nije kvar.

## Korisnik javi grešku s brojem

Poruka za grešku `500` nosi `requestId`, a isti broj je u zaglavlju `X-Request-Id` svakog odgovora. Na Renderu je log JSON, pa se u Logs traži taj broj, i nađu se sve linije tog zahtjeva sa stack traceom. U Sentryju (kad je `SENTRY_DSN` postavljen) je isti broj tag `requestId`, pa se pretraži `requestId:<broj>`.

## Metrike

Lokalno: `GET http://localhost:8080/actuator/metrics/slotbook.bookings.created` (isto za `slotbook.bookings.conflicts` i `slotbook.mail.failed`), s tokenom prijavljenog korisnika. Na produkciji metrike namjerno nisu izložene (vidi `docs/ODLUKE.md`, "Metrike: tri brojača").

## Backup i vraćanje

Backup je `pg_dump` u custom formatu (`-Fc`), a vraća se s `pg_restore`. Klijent mora biti iste ili novije glavne verzije od servera, zato ide preko Docker imagea `postgres` s brojem verzije servera. Dump nosi emailove i hasheve lozinki, pa ide u folder `backup/`, koji je u `.gitignore`, a ne u repo.

Backup nije dokazan dok nije vraćen u praznu bazu i dok se broj redova ne poklopi. Za brojanje je [`mjerenja/broj-redova.sql`](mjerenja/broj-redova.sql), koji ispiše broj redova svake tabele sortiran po imenu, pa se dva ispisa porede s `Compare-Object` ili `diff`.

### Provjereno lokalno, 22.11.2026.

Baza je bila `opterecenje` poslije k6 testa (`docs/PERFORMANSE.md`), migrirana kroz Flyway same aplikacije, u kontejneru `postgres:17` (PostgreSQL 17.11). Imala je 15 tabela, 631 rezervaciju, 631 zapis historije, 631 obavijest, 300 refresh tokena i 101 korisnika.

```powershell
docker exec slotbook-explain pg_dump -U postgres -d opterecenje -Fc -f /tmp/slotbook.dump
docker exec slotbook-explain psql -U postgres -c "create database vraceno"
docker exec slotbook-explain pg_restore -U postgres -d vraceno --no-owner --exit-on-error /tmp/slotbook.dump
Get-Content docs\mjerenja\broj-redova.sql | docker exec -i slotbook-explain psql -U postgres -d opterecenje -tA > prije.txt
Get-Content docs\mjerenja\broj-redova.sql | docker exec -i slotbook-explain psql -U postgres -d vraceno -tA > poslije.txt
Compare-Object (Get-Content prije.txt) (Get-Content poslije.txt)
```

Dump je imao 199 KB i trajao je 408 ms, a vraćanje 570 ms. `diff` je bio prazan, isti broj redova u svih 15 tabela. U vraćenoj bazi su i `flyway_schema_history` do verzije 33, `btree_gist` i oba constrainta na `bookings`. `EXCLUDE` je u vraćenoj bazi odbio dupli termin sa `23P01`. Na kraju sam aplikaciju pokrenuo nad vraćenom bazom: Flyway je validirao 33 migracije ("No migration necessary"), Hibernate `validate` je prošao, a readiness je vratio `UP`.

Komande gore su iz Git Bash-a prepisane za PowerShell, i u PowerShellu ih nisam pokretao.

### Neon, provjereno 22.11.2026.

Podaci za spajanje su u Neon konzoli: Connect, pa isključiti "Connection pooling", jer dump treba direktnu adresu (host bez `-pooler`), kao i Flyway. Host, baza i korisnik nisu tajna, lozinka jeste. Skripta ispod je traži jednom (`Read-Host -AsSecureString`) i drži je samo u varijabli okruženja `PGPASSWORD` tog prozora. Dockeru se prosljeđuje kao `-e PGPASSWORD`, bez vrijednosti u komandi, a na kraju se briše. Verziju servera skripta pita Neon, pa koristi isti image (Neon je bio na 17).

```powershell
$env:PGHOST = "<host bez -pooler>"
$env:PGDATABASE = "neondb"
$env:PGUSER = "neondb_owner"
$env:PGSSLMODE = "require"
$sigurna = Read-Host "Neon lozinka" -AsSecureString
$env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sigurna))
try {
    $neon = @("-e", "PGHOST", "-e", "PGDATABASE", "-e", "PGUSER", "-e", "PGPASSWORD", "-e", "PGSSLMODE")
    $glavna = [int]((docker run --rm @neon postgres:17 psql -tAc "show server_version_num").Trim().Substring(0, 2))
    New-Item -ItemType Directory -Force backup | Out-Null
    docker run --rm @neon -v "${PWD}\backup:/backup" "postgres:$glavna" pg_dump -Fc -f /backup/neon.dump
    docker run --rm @neon -v "${PWD}\docs\mjerenja:/sql:ro" "postgres:$glavna" psql -tA -f /sql/broj-redova.sql > backup\neon-prije.txt
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    $sigurna = $null
}
```

Vraćanje u praznu lokalnu bazu. Običan `pg_restore` ovdje ne radi. Neon u dump upiše svoju opciju kolone, `ALTER TABLE ... ALTER COLUMN ... SET (pg_attver=...)`, koju običan Postgres ne poznaje. Prvi pokušaj je zato pao sa `unrecognized parameter "pg_attver"`: 10 od 15 tabela nije napravljeno, a uz njih ni podaci, indeksi i strani ključevi (62 greške). Dump se zato prvo pretvori u SQL, iz njega se izbace samo te linije, i pusti se kroz `psql`, koji staje na prvoj grešci:

```powershell
docker run -d --rm --name slotbook-vracanje -e POSTGRES_PASSWORD=samo-za-provjeru "postgres:$glavna"
docker cp backup\neon.dump slotbook-vracanje:/tmp/neon.dump
docker exec slotbook-vracanje sh -c "pg_restore --no-owner --no-privileges -f /tmp/neon.sql /tmp/neon.dump && sed -i '/SET (pg_attver=/d' /tmp/neon.sql"
docker exec slotbook-vracanje psql -U postgres -c "create database vraceno"
docker exec slotbook-vracanje psql -U postgres -d vraceno -q -v ON_ERROR_STOP=1 -f /tmp/neon.sql
Get-Content docs\mjerenja\broj-redova.sql | docker exec -i slotbook-vracanje psql -U postgres -d vraceno -tA > backup\neon-poslije.txt
Compare-Object (Get-Content backup\neon-prije.txt) (Get-Content backup\neon-poslije.txt)
docker stop slotbook-vracanje
```

Rezultat 22.11.2026: dump je imao 49 512 bajta i trajao je 8 s, preko mreže do Neona u Frankfurtu. U njemu su bile 73 linije `pg_attver`, sve izbačene. Vraćanje je trajalo 521 ms i prošlo bez greške, a broj redova je isti u svih 15 tabela. U vraćenoj bazi su Flyway historija do verzije 33, oba `EXCLUDE` constrainta i `bookings_najvise_osam_sati`. Na Neon-u je bilo malo podataka (jedan korisnik), pa ovo dokazuje postupak, ne brzinu. Pretvaranje i vraćanje sam pokrenuo iz Git Bash-a, a dump i brojanje na Neon-u iz PowerShella.

Šta nije provjereno: vraćanje u novu Neon bazu, gdje bi `pg_attver` vjerovatno prošao i bez izbacivanja, i Neonovo vraćanje na raniji trenutak (branch iz prošlosti). Koliko unazad to ide na besplatnom planu nisam gledao.
