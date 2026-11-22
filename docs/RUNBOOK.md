# Runbook

Šta uraditi kad nešto ne radi, i kako se pravi i vraća backup. Sve komande su za PowerShell na Windowsu, s Dockerom. Lozinke se nikad ne pišu u komandu. `psql` i `pg_dump` ih traže sami, pa ne ostaju u historiji terminala.

## Da li aplikacija radi

`/actuator/health/liveness` kaže da je proces živ. `/actuator/health/readiness` kaže da prima zahtjeve i da baza odgovara. Render gleda readiness (Settings > Health Check Path: `/actuator/health/readiness`).

```powershell
curl.exe https://slotbook-dxvs.onrender.com/actuator/health/readiness
```

`{"status":"UP"}` je dobro. `503` s `"status":"DOWN"` znači da baza ne odgovara. Tada prvo pogledati Neon konzolu (da li je projekat suspendovan ili prekoračio limit), pa log na Renderu. Prvi zahtjev poslije spavanja traje i do minute. To je hladni start besplatnog plana, nije kvar.

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

### Neon

Ovo još nije urađeno, radi se ručno. Podatke za spajanje daje Neon konzola: Connect, pa isključiti "Connection pooling", jer dump treba direktnu adresu, kao i Flyway. Verziju servera vraća `select version();` u Neonovom SQL Editoru. Ispod je `postgres:17`. Ako je Neon na drugoj verziji, broj se mijenja.

1. Backup (traži lozinku):

   ```powershell
   New-Item -ItemType Directory -Force backup
   docker run --rm -it -v "${PWD}\backup:/backup" postgres:17 pg_dump "host=<HOST> dbname=<BAZA> user=<KORISNIK> sslmode=require" -Fc -f /backup/neon.dump
   ```

2. Brojanje na Neon-u (traži lozinku):

   ```powershell
   Get-Content docs\mjerenja\broj-redova.sql | docker run --rm -i postgres:17 psql "host=<HOST> dbname=<BAZA> user=<KORISNIK> sslmode=require" -tA > backup\neon-prije.txt
   ```

   Ako za vrijeme ova dva koraka neko pravi rezervaciju, brojevi se neće poklopiti. Na demo bazi to se ne očekuje.

3. Vraćanje u praznu lokalnu bazu:

   ```powershell
   docker run -d --rm --name slotbook-vracanje -e POSTGRES_PASSWORD=samo-za-provjeru postgres:17
   docker cp backup\neon.dump slotbook-vracanje:/tmp/neon.dump
   docker exec slotbook-vracanje psql -U postgres -c "create database vraceno"
   docker exec slotbook-vracanje pg_restore -U postgres -d vraceno --no-owner --no-privileges /tmp/neon.dump
   Get-Content docs\mjerenja\broj-redova.sql | docker exec -i slotbook-vracanje psql -U postgres -d vraceno -tA > backup\neon-poslije.txt
   Compare-Object (Get-Content backup\neon-prije.txt) (Get-Content backup\neon-poslije.txt)
   docker stop slotbook-vracanje
   ```

   `--no-owner --no-privileges` je tu jer su na Neon-u vlasnici i prava za Neonove uloge, kojih lokalno nema. Ako `pg_restore` javi greške za te uloge, zapisati koje su.

Rezultat (datum, veličina dumpa, trajanje, ishod poređenja) ide ovdje, umjesto ove rečenice.

Neon ima i svoje vraćanje na raniji trenutak (branch iz prošlosti). Koliko unazad ide na besplatnom planu nisam provjerio, a ni to vraćanje nisam probao.
