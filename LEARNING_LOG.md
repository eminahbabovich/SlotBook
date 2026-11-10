# Dnevnik učenja

## utorak, 10. novembar 2026. u 14:54

Prvi dan SlotBooka. Do kraja dana imam aplikaciju na Renderu koja iz baze na Neon-u vraća `/api/status`. To je jedini endpoint, ali sve oko njega radi: testovi, CI, Docker slika.

Najviše vremena su pojele gluposti. `docker compose up -d` je pao s `Only one usage of each socket address (protocol/network address/port) is normally permitted`. Port 5432 već drži `roomsplit-db` iz prvog projekta. Promijenio sam port u `.env` na 5433, ali tek poslije `up`, pa sam dobio istu grešku i mislio da to nije uzrok. Prošlo je kad sam ponovo pokrenuo.

Aplikacija iz IntelliJ-a je odmah završila s `exit code 1`. Spring ne čita `.env`, to radi samo Docker Compose, pa sam ga morao dodati u run konfiguraciju. U `.env.example` mi je u međuvremenu bila upisana prava lozinka. Nije bila commitana, pa sam fajl vratio.

Na Neon-u sam pokrenuo `insert` s preklapanjem termina i dobio crvenu poruku `conflicting key value violates exclusion constraint ... (SQLSTATE 23P01)`. Prvo sam je prijavio kao grešku, a to je bio upravo test koji je trebao pasti. Insert prije toga, termini 9-10 i 10-11, prošao je jer se kraj raspona ne računa.

Za Render sam u `DB_URL` prvo stavio host s `-pooler`. Treba direktna adresa, jer Neon piše da pooler ne podržava sesijske advisory lockove, a Flyway ih koristi.

Naučio sam da se `Instant.now()` ne može testirati, pa vrijeme ulazi kroz `Clock`, i da se Flyway migracija ne mijenja kad se jednom izvrši, nego ide nova. Iznenadilo me koliko brzo korake prolazimo, očekivao sam da će trebati više vremena.
