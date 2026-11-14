# Dnevnik učenja

## utorak, 10. novembar 2026. u 14:54

Prvi dan SlotBooka. Do kraja dana imam aplikaciju na Renderu koja iz baze na Neon-u vraća `/api/status`. To je jedini endpoint, ali sve oko njega radi: testovi, CI, Docker slika.

Najviše vremena su pojele gluposti. `docker compose up -d` je pao s `Only one usage of each socket address (protocol/network address/port) is normally permitted`. Port 5432 već drži `roomsplit-db` iz prvog projekta. Promijenio sam port u `.env` na 5433, ali tek poslije `up`, pa sam dobio istu grešku i mislio da to nije uzrok. Prošlo je kad sam ponovo pokrenuo.

Aplikacija iz IntelliJ-a je odmah završila s `exit code 1`. Spring ne čita `.env`, to radi samo Docker Compose, pa sam ga morao dodati u run konfiguraciju. U `.env.example` mi je u međuvremenu bila upisana prava lozinka. Nije bila commitana, pa sam fajl vratio.

Na Neon-u sam pokrenuo `insert` s preklapanjem termina i dobio crvenu poruku `conflicting key value violates exclusion constraint ... (SQLSTATE 23P01)`. Prvo sam je prijavio kao grešku, a to je bio upravo test koji je trebao pasti. Insert prije toga, termini 9-10 i 10-11, prošao je jer se kraj raspona ne računa.

Za Render sam u `DB_URL` prvo stavio host s `-pooler`. Treba direktna adresa, jer Neon piše da pooler ne podržava sesijske advisory lockove, a Flyway ih koristi.

Naučio sam da se `Instant.now()` ne može testirati, pa vrijeme ulazi kroz `Clock`, i da se Flyway migracija ne mijenja kad se jednom izvrši, nego ide nova. Iznenadilo me koliko brzo korake prolazimo, očekivao sam da će trebati više vremena.

## četvrtak, 12. novembar 2026. u 23:51

Poslijepodne sam morao ugasiti laptop usred koraka s organizacijama, i uveče nisam znao da li sam na koraku 5 ili 6. Bio je 6. Kod je bio napisan, a falio je HTTP test.

Najviše me iznenadilo `@EnableMethodSecurity`. Kad ga obrišem, aplikacija se normalno pokrene, `@PreAuthorize` se tiho preskoči i Tarik, admin druge organizacije, dobije `200` i tuđe emailove. Nema ni upozorenja u logu. Zato sada test izolacije sam čita sve rute iz Springa i padne ako postoji endpoint nad organizacijom koji nije u testu.

Kod pozivnica sam htio obrisati staru pozivnicu pa upisati novu, i to je palo s `duplicate key value violates unique constraint "organization_invites_jedna_otvorena"`. Hibernate pri flushu radi INSERT prije DELETE-a, bez obzira kojim redom ih pozovem. Bulk `delete` s `@Modifying` ide u bazu odmah i tu radi.

Pustio sam 8 niti da prihvate istu pozivnicu bez uslova `accepted_at IS NULL` u UPDATE-u i očekivao `500`. Dobio sam jedan OK i sedam "već je član". UPDATE zaključa red i bez uslova, pa ostali čekaju. Uslov ipak ostaje, jer bez njega iskorištena pozivnica ne bi ostala iskorištena kad budem mogao ukloniti člana.

Claude je pri mjerenju N+1 upita vratio fajl s `git checkout` i time obrisao upit koji smo tek napisali. Kasnije je skripta za refaktor ostavila zagradu viška, a Maven je uz grešku kompajliranja ispisao stare zelene izvještaje. Sad se izvještaji brišu prije svakog pokretanja.

CI je bio crven od 18:29 i niko to nije gledao. Gitleaks je lažni `JWT_SECRET` u `.env.example` prijavio kao `generic-api-key`, a Render je za to vrijeme sve uredno deployao jer ne čeka CI. Promjena vrijednosti ne bi pomogla jer stari commit ostaje u historiji, pa u `.gitleaksignore` ide samo taj jedan nalaz. Fazu 2 sam označio gotovom prije nego što sam pogledao Actions.

## petak, 13. novembar 2026. u 19:47

Cijela Faza 3 za jedan dan, od zone organizacije do slike resursa. Ujutro mi je jedan podkorak trajao po deset minuta, pa sam tražio da se ubrza. Od tada se sve mutacije vrte u jednom Maven pokretanju, a cijela suita samo na kraju koraka.

`Etc/GMT-1` je sad odbijen kao zona. To je fiksni pomak bez ljetnog vremena, a uz to je znak obrnut, pa je zapravo +01:00. Provjera je `isFixedOffset()`, i odbija i `UTC`.

Najviše me prepalo sortiranje. Kad sam isključio da server sam bira redoslijed, `?sort=provider.passwordHash` je vratio `200`, pa se resursi mogu poredati po hashu lozinke providera. Sad server ignoriše klijentov sort.

Claude je tvrdio da lista članova treba poseban `countQuery`, jer "Hibernate odbija count s join fetch". Izbacili smo ga privremeno i sve je radilo, pa je izbačen zauvijek. U jednom `@DataJpaTest` sam stavio dva INSERT-a koja trebaju pasti, i drugi je pao s `current transaction is aborted`, a ne s porukom koju sam čekao. Postgres poslije prve greške ne prima ništa do kraja transakcije, pa su to sad dva testa.

Za radno vrijeme Postgres nema raspon za `TIME`, pa migracija pravi svoj tip, `CREATE TYPE timerange AS RANGE (subtype = time)`. Bojao sam se da Neon to neće pustiti, ali prošlo je iz prve.

DST test je pokazao ono što sam htio vidjeti. Kad se pomak zone uzme za trenutak upita (`getOffset(now)`), a ne za datum termina, padnu sva četiri DST testa, a zimski i onaj s `Asia/Tokyo` prođu. Takav bug bi živio do zadnje nedjelje u martu. U noći kad se sat vraća, pravilo od 00 do 04 ima pet satnih termina, a ne četiri.

Kod slike se pokazalo da MinIO više nema javne Docker slike, `pull access denied for minio/minio, repository does not exist`. Test je prešao na `adobe/s3mock`. Claude je usput prepisao postojeći `GlobalExceptionHandlerTest` bez čitanja, i vratio ga iz gita. Test da S3 bez endpointa ne starta je prvo prolazio iz pogrešnog razloga: u malom kontekstu `${...}` ostane običan tekst i padne `URI.create`, a ne placeholder.

Bez `@PreAuthorize` na uploadu Bojan iz druge organizacije je dobio `200` i stvarno stavio sliku na sto u tuđoj čitaonici. Test izolacije je to uhvatio, ali tek kad je naučio slati multipart.

## subota, 14. novembar 2026. u 12:14

Cijela Faza 4 u jednoj sesiji, od tabele `bookings` do historije rezervacije.

Djelimičan `EXCLUDE` (s `WHERE status IN ('PENDING', 'CONFIRMED')`) sam prvo probao na Neon-u na probnoj tabeli, ne na `bookings`, jer bi Flyway pao da tabela već postoji. Preklapanje je palo sa `23P01`, i probnu tabelu sam obrisao.

Test sa 20 niti me najviše naučio. Prvi put: jedna rezervacija u bazi, kako treba, ali 19 odgovora `500` i test od 85 sekundi. U logu `deadlock detected ... while checking exclusion constraint on tuple (0,1) in relation "bookings"`, svih 19 sa `40P01`. Mislio sam da se `EXCLUDE` ponaša kao `UNIQUE` i da drugi uvijek dobije `23P01`, a i Claude je to tvrdio u koraku prije. Postgres prvo upiše red pa traži sukobe, pa dvije transakcije čekaju jedna na drugu. Sad se prvo zaključa resurs. Onda me iznenadilo da bez locka test zna i proći, dvaput zaredom, pa sada ima 4 runde.

Isti lock je spasio `Idempotency-Key`. Kad se ključ traži prije locka, 10 istih zahtjeva da 1 × `201` i 9 × `409`, pa bi mi dupli klik javio da je moj termin zauzet.

Claude je pogriješio četiri puta. Grep prije promjene potpisa nije našao `resourceService.create(...)` u testu radnog vremena, pa nije prošlo kompajliranje. U testu s nitima je `JsonPath` tražio `$.id` u `409` odgovoru. Pomoćna metoda `status(UUID)` je pregazila `status()` iz MockMvc-a. I dvije mutacije pustio je odjednom, pa je isključen rok za otkazivanje sakrio da kontroler ne javlja ko vodi resurs.

`S3StorageServiceTest` je jednom pao usred cijele suite s `The target server failed to respond (SDK Attempt Count: 4)`, a sam je prošao dvaput. Nisam ga dirao. `jacoco.exec` je narastao na 82 MB, jer se u njega dopisivalo svako pokretanje, i ono s mutacijama, pa sam pokrivenost mjerio tek kad sam ga obrisao.
