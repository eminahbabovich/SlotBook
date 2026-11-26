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

## Deploy frontenda na Vercel

Prođeno 25.11.2026, adresa je `https://slot-book-delta.vercel.app`. Vercel je sam prepoznao Vite, a Build Command, Output Directory i Install Command ostaju na podrazumijevanim vrijednostima (prekidači isključeni). Rezultati provjera su na kraju ove sekcije.

Na vercel.com: Add New, Project, pa import repozitorija `SlotBook` s GitHuba. Root Directory je `frontend`. Framework Preset prepozna Vite, Build Command ostaje `npm run build`, a Output Directory `dist`. Varijable okruženja frontend ne treba, jer adresu backenda nosi `frontend/vercel.json`. Ako Render servis ikad dobije drugu adresu, mijenja se tamo. Deploy.

Poslije prvog deploya na Renderu (Environment) treba promijeniti `APP_URL` na adresu s Vercela (`https://<projekat>.vercel.app`, bez kose crte na kraju). Iz nje backend pravi linkove u mailovima, a CORS dozvoljava samo nju.

Provjera, redom:

```powershell
curl.exe -i https://<projekat>.vercel.app/api/status
curl.exe -i https://<projekat>.vercel.app/o/citaonica-pmf
```

Prvi treba vratiti JSON s backenda i zaglavlje `x-request-id`, a drugi `index.html`, ne `404`. Onda prijava u browseru, pa DevTools, Network: `/api/auth/login` vraća `Set-Cookie` s refresh tokenom, a poslije osvježavanja stranice `/api/auth/refresh` vraća `200` i sesija ostaje. Na kraju kalendar u dva taba: rezervacija u jednom treba se pojaviti u drugom bez osvježavanja. Ako se ne pojavi, SSE ne prolazi kroz Vercel. Kalendar tada i dalje radi, samo se ne osvježava sam.

Rezultat 25.11.2026 (curl s mog računara): `/api/status` kroz Vercel vraća JSON s `X-Render-Origin-Server: Render`, a dva ista zahtjeva imaju `X-Vercel-Cache: MISS` i različit `X-Request-Id`, znači da Vercel API ne kešira. `/o/citaonica-pmf` vraća `index.html`. Preflight vraća `Access-Control-Allow-Origin` s Vercel adresom. Prijava vraća refresh kolačić (`HttpOnly; Secure; SameSite=Strict; Path=/api/auth`), a `/api/auth/refresh` samo s tim kolačićem vraća `200`. SSE tok se otvori s `text/event-stream`, rezervacija i otkazivanje stignu kao dva `event:promjena` dok je tok otvoren, a tok je ostao otvoren punih 400 s, koliko je curl čekao. Dva taba u browseru nisam probao, samo curl. Granica pokušaja po IP-u kroz Vercel ne radi, vidi `docs/ODLUKE.md`.

## Demo na Renderu

Profil `demo` na pokretanju OBRIŠE SVE iz baze i upiše demo podatke. Pali se samo na bazi koja služi samo za demo. Na Renderu: Environment, `SPRING_PROFILES_ACTIVE` = `prod,demo`, pa "Save, rebuild, and deploy" (rebuild, da sigurno bude zadnji commit s demo kodom). Render poslije toga sam pokrene novi deploy, a u logu se vidi `Demo podaci su vraćeni na početak`. Provjera: `curl.exe https://<backend>/api/demo` vraća tri računa. Bez profila `demo` isti zahtjev vraća `404`.

Demo se sam vrati na početak kad je stariji od 24 sata, ili kad nekog od tri računa nema. Restart servisa ga vraća samo ako je stariji od dana. Za ručno vraćanje se u Neon SQL editoru postare računi, pa restart:

```sql
update users set created_at = created_at - interval '2 days' where email like '%@slotbook.test';
```

## Ostale varijable za produkciju

Stripe (test mode): `STRIPE_SECRET_KEY` (`sk_test_...`) i `STRIPE_WEBHOOK_SECRET`. Webhook se dodaje u Stripe dashboardu (Developers, Webhooks, Add endpoint) na `https://<backend>/api/stripe/webhook`, s događajima `checkout.session.completed` i `checkout.session.expired`, a njegov `whsec_` ide u `STRIPE_WEBHOOK_SECRET`. Webhook ide direktno na Render, ne preko Vercela. U novom dashboardu je to Workbench, Webhooks, "Create an event destination", tip "Webhook endpoint", a `whsec_` je na stranici endpointa pod "Signing secret". To nije isti `whsec_` koji daje `stripe listen` lokalno. Provjereno 26.11.2026: rezervacija Sale 3 u demu, plaćanje karticom `4242 4242 4242 4242` na Checkout stranici, rezervacija prešla u `CONFIRMED`, otkazivanje vratilo `200` (povrat ide sinhrono, pa bi odbijen povrat poništio otkazivanje). Ako uplata prođe a rezervacija ostane `PENDING`, prvo pogledati isporuke u Stripeu kod endpointa: `400` znači pogrešan `STRIPE_WEBHOOK_SECRET`.

Push: `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY` (iz `npx web-push generate-vapid-keys`) i `VAPID_SUBJECT` = `mailto:adresa` bez razmaka iza dvotačke. Ključevi se ne mijenjaju, jer bi svi uređaji izgubili pretplatu. Provjera: `GET /api/push/public-key` s tokenom vraća `enabled: true` (26.11.2026).

SMTP: Render na besplatnom planu blokira odlazne portove 25, 465 i 587 (render.com/docs/free, od septembra 2025), pa mail ide na port 2525 sa STARTTLS-om, koji je uključen u `application-prod.properties`. Servis je Brevo (Senders: adresa pošiljaoca potvrđena mailom; SMTP & API, tab SMTP: Login i SMTP ključ, koji se pokaže samo jednom). Na Renderu: `MAIL_HOST` = `smtp-relay.brevo.com`, `MAIL_PORT` = `2525`, `MAIL_FROM` = `SlotBook <adresa@gmail.com>` (s `< >`, bez navodnika; bez zagrada se mail ne može sastaviti, a greška stigne u Sentry), `SPRING_MAIL_USERNAME` = Login iz Brevoa (nije Gmail adresa) i `SPRING_MAIL_PASSWORD` = SMTP ključ. Za promjenu samo varijable je dovoljno "Save and deploy", bez rebuilda. Provjera: registracija na Vercel adresi, mail stigne (zasad u spam), link vodi na Vercel, prijava prolazi. Ako ne stigne, `select type, status, attempts, last_error from notifications order by created_at desc limit 3` u Neonu kaže zašto. `SPRING_MAIL_PROTOCOL` = `smtps` se ne koristi: timeouti su podešeni za `mail.smtp.*`, a `smtps` čita `mail.smtps.*`, pa bi slanje moglo visiti bez granice. Provjereno 25.11.2026. Demo računi ionako ne primaju mail, ali bez SMTP-a ne može se napraviti nov nalog, jer potvrda emaila ne stigne.

Slike (R2): `STORAGE_TYPE` = `s3` i `S3_ENDPOINT`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`. Bez toga slike nestanu sa svakim deployem. Na Renderu zasad nije podešeno (`STORAGE_TYPE` je `local`), vidi `docs/ODLUKE.md`.

Uptime monitor: UptimeRobot (besplatni plan, prijava preko GitHuba) na `https://slotbook-dxvs.onrender.com/actuator/health/liveness` svakih 5 minuta, od 26.11.2026. Drži Render budnim, pa posjetilac ne čeka hladni start, a kad backend padne, stigne mail. Liveness ne pita bazu, pa Neon i dalje može spavati. Render besplatni plan ima 750 sati mjesečno po nalogu, ne po servisu. Jedan servis budan cijeli mjesec troši najviše 744, pa stane, ali drugi besplatni servis na istom nalogu koji se isto drži budnim ne bi stao. Koliko se sati stvarno potroši, još nisam gledao (Render, Billing).
