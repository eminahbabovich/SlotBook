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

## subota, 14. novembar 2026. u 18:28

Faza 5 od početka do kraja, devet koraka. U plan je na početku ušla i Faza 14, AI asistent, između Faze 12 i 13.

Mutacije iz koraka 6 su bile lažno zelene. Skripta je zvala Maven preko `cmd /c mvnw.cmd`, a `cmd` je odgovarao `'mvnw.cmd' is not recognized as an internal or external command`. Exit kod je bio 1, pa je svaka mutacija "pala", a nijedan test se nije pokrenuo. Shvatio sam to tek u koraku 7, kad je surefire izvještaj imao pet testova za mutaciju koja je pokretala jedan. Sad se mutacija računa samo ako u izlazu piše `<<< FAILURE!`, i ponovio sam ih sve.

Najviše me iznenadio istek na dvije instance. Kad je `UPDATE` imao uslov `status = 'PENDING'` direktno u `WHERE`, drugi posao je čekao red i onda ga preskočio. Kad sam isti uslov stavio u podupit (`WHERE id IN (SELECT ...)`), drugi je istekao iste rezervacije još jednom. Postgres poslije čekanja ponovo provjeri samo uslov na redu, a podupit ostane iz starog snimka. Zbog toga nema ShedLocka, a test sada čeka u `pg_stat_activity` dok drugi stvarno ne čeka na lock, umjesto da se nada da će se niti sudariti.

Isti test za podsjetnike je prvo pao s "drugi posao nikad nije čekao na lock". Hibernate upiše novi red tek pri commitu, pa prva transakcija dok je otvorena nije još ništa poslala bazi. Trebao je `flush`.

Mail health indicator je vratio `503` na `/actuator/health` jer lokalno nije bilo SMTP-a. Na Renderu bi to oborilo deploy, pa je isključen. Jedan test je prošao iz pogrešnog razloga: trebao je pasti na `notifications_poslana`, a pao je na `notifications_rezervacija`. Obrisan je. Korak 4 je commitan prije nego što je suite završio, pa je ispravljen s `--amend` dok još nije bio pushan. Od tada se commit radi tek kad pročitam rezultat.

Claude je u resetu lozinke pozvao `user.getPasswordChangedAt()`, koja ne postoji, a prvi unit test za reset nije mogao pasti, jer Mockito za `Optional` sam vrati `Optional.empty()`. U podsjetniku je Postgres javio `operator does not exist: timestamp with time zone <= interval` za `:sada + make_interval(...)`, pa se granica sada računa u Javi. Datum prelaska na ljetno vrijeme 2036. je prvo napisao kao 29.3., a nedjelja je 30.

Naslov "Pozivnica u Čitaonica PMF" ne valja jer se naziv ne može deklinirati, pa je sad "Pozivnica na SlotBook: Čitaonica PMF". Za test ponovnog slanja Mailpit je pauziran preko Dockera, i prvi pokušaj je pao s `Exception reading response` poslije pet sekundi, tačno koliko je timeout.

## nedjelja, 15. novembar 2026. u 13:35

Sesija bez nove faze, sređivao sam alate. Instalirao sam graphify da Claude prvo gleda graf koda prije nego što otvara fajlove. Prvi graf nije imao nijednu migraciju: `26 .sql file(s) contributed nothing to the graph because a dependency is missing: tree_sitter_sql`. Sa `graphifyy[sql]` je izašlo 1798 čvorova. rtk nisam instalirao. Nemam ni brew ni cargo, a `cargo install rtk` bi ionako povukao drugi projekat s istim imenom.

Usput je ispao bug iz Faze 4. Handler za nedozvoljen prelaz je radio `setProperty("status", ...)`, a Spring `properties` iz `ProblemDetail` ispisuje na vrh JSON-a preko `@JsonAnyGetter` (provjerio sam `javap` na `spring-web-7.0.10.jar`). Na `409` je u polju `status` stajao `"CANCELLED"`, ne `409`. Test to nije uhvatio jer je potvrđivao baš to: `jsonPath("$.status").value("CANCELLED")`. Kad sam ga promijenio da traži `409`, prvo nije ni krenuo jer Docker nije bio upaljen (`Could not find a valid Docker environment`). Onda je pao s `value <CANCELLED> of type <java.lang.String> cannot be converted to type <java.lang.Integer>`. Polje se sada zove `trenutniStatus`, a svih 307 testova prolazi.

Iznenadilo me da globalni skill u `~/.claude/skills/` pobjeđuje projektni s istim imenom. Zato projektni sada imaju prefiks `slotbook-`.

## nedjelja, 15. novembar 2026. u 15:05

Cijela Faza 6 u jednoj sesiji, statistika u devet koraka. Najviše sam naučio na `extract(hour FROM start_time)`. Bez `AT TIME ZONE` radi u zoni konekcije, pa su se dvije srijede u 9:00, jedna prije i jedna poslije 28. oktobra, raspale na 7 i 8 sati. U mutaciji je ponoćni termin u ponedjeljak ispao kao nedjelja u 23 h. `isodow` broji od ponedjeljka kao `java.time`, a obični `dow` od nedjelje kao nule.

Record iz native upita radi direktno u Spring Data JPA 4.1.2. To sam prvo vidio u bytecodu (`isDtoProjection`, `instantiateDto`), a onda u testu. `extract` vraća `numeric`, pa ide `CAST(... AS integer)`, inače konstruktor recorda s `int` ne bi odgovarao.

Za popunjenost sam htio pisati SQL nad radnim vremenom, ali sam uzeo isti `SlotCalculator` koji pravi kalendar. Usput se pokazalo da on vraća samo buduće termine, a statistika gleda prošlost, pa je dobio `allBetween`. Testovi kalkulatora iz Faze 3 su prošli bez izmjene.

`UnfinishedStubbingException` u Mockitu: `mockResursa.getId()` sam pozvao unutar `when(...).thenReturn(...)` za drugi mock. ID sad pravim prije.

Test izolacije nije pokrivao CSV. Šalje zahtjev bez `Accept`, pa uvijek pogodi JSON metodu, a CSV metoda ima svoj `@PreAuthorize`. Dodao sam poseban test s adminom tuđe organizacije.

`EXPLAIN` na 408 hiljada redova je potvrdio sumnju: statistika jedne čitaonice za mjesec je čitala cijelu tabelu, jer je jedini indeks bio parcijalan, onaj iza `EXCLUDE`. S indeksom `(resource_id, start_time)` mjesec je pao s oko 20 na oko 2 ms, a godina se nije pomjerila. Kad sam za godinu zabranio seq scan, indeks je dotakao 20 552 stranice, a cijela tabela ih ima 7 010, pa planer bira dobro. Git Bash mi je `/migracije` u `docker exec` pretvorio u `C:/Program Files/Git/migracije`, pa je trebao `MSYS_NO_PATHCONV=1`.

Pitao sam Claude-a zašto svaki korak traje par minuta. Dogovorili smo se 14.11. da mutacije idu u jednom Maven pokretanju, a on ih je pokretao svaku posebno, oko 18 puta. Cijela suite (2:28) ide sada samo prije pusha.

## nedjelja, 15. novembar 2026. u 23:11

Faza 7 od kostura do rasporeda za providera, devet koraka u jednom danu. TypeScript sam izbacio odmah na početku, jer ga ne znam, a ne želim kod koji ne mogu objasniti.

Najgori bug je bio odjava. Hana se odjavi, Adnan se prijavi na istom računaru i završi na njenoj adresi `/o/citaonica-pmf/...`. Prvi popravak, `navigate` u zaglavlju, nije ništa promijenio, jer se `<Navigate>` iz čuvara izvršio poslije njega i opet zapamtio adresu. Prošlo je tek kad je sesija dobila posebno stanje "odjavio se", u kojem čuvar ne pamti odakle je korisnik došao.

Edge 157 nema bosanske podatke u `Intl`. Umjesto "ponedjeljak, 19. novembar" ispisao je "M11 19, Mon". Nazive dana i mjeseci sad imam u svojim nizovima, a test glumi browser bez `bs`.

Test za rezervaciju na čekanju sam prvo napisao tako da direktno ubaci red u bazu, i pao je na `bookings_pending_ima_rok`. Baza je bila u pravu: `PENDING` bez roka ne smije postojati. Test sad rezerviše kroz API.

U browseru sam vidio "Otkazati možeš do ponedjeljak". Na bosanskom bi trebalo "do ponedjeljka", a pisati sklanjanje dana mi se ne isplati, pa sad piše "Rok za otkazivanje: ponedjeljak, ...".

Kad je admin dobio link "Raspored: Sto 4, tihi dio", pao je stari test kalendara koji je link tražio regexom `/Sto 4, tihi dio/`. Kod je bio ispravan, a upit u testu preširok.


## srijeda, 18. novembar 2026. u 18:49

Admin dio Faze 7: članovi, pozivnica, prihvatanje pozivnice i resursi. Radno vrijeme i izuzeci ostaju za 10c.

Dvije poruke na backendu su obećavale nešto čega nema. "Ulogu joj možeš promijeniti među članovima", a promjene uloge nema nigdje. "Pogledaj listu pozivnica", a te liste isto nema. Dok nije bilo ekrana, niko ih nije čitao. Ispravio sam tekst, a lista pozivnica i promjena uloge su otišle u Dodatke.

Link iz maila za pozivnicu je od Faze 5 vodio na `/pozivnica#token=...`, a te stranice nije bilo. Kad sam je napravio, ispalo je da bi se token ionako izgubio: čuvar ruta je pamtio samo `pathname`, bez dijela iza `#`, a link "Napravi nalog" na prijavi nije nosio zapamćenu adresu dalje. Pozvana osoba skoro nikad nema nalog, pa bi se registrovala i završila na početnoj.

Mutacija koju test nije uhvatio: izbacio sam osvježavanje liste organizacija poslije prihvatanja, i sve je prošlo. Probao sam usporiti lažni backend na 200 ms, opet je prošlo. Tek onda sam pogledao ko na `/pozivnica` uopšte čita tu listu. Niko, pa je keš prazan i nema šta biti zastarjelo. Trebalo je to provjeriti prije nego što sam mijenjao test.

`PUT` resursa bez `autoConfirm` vrati automatsku potvrdu. To sam ja napisao u Fazi 3 i zaboravio. Forma zato šalje sva polja.

Testovi su dvaput pali zbog upita, a ne zbog koda. `findByText('Hana Šehić')` je našao ime u zaglavlju prije nego što se lista članova učitala. "Nijedan POST" nije prošlo, jer je i obnova sesije POST.


## srijeda, 18. novembar 2026. u 19:04

10c, radno vrijeme i izuzeci. Ovim je korak 10 gotov.

API sloj za radno vrijeme sam napisao prije testa, iako je pravilo obrnuto. Da bih vidio da testovi ipak nešto čuvaju, kasnije sam ubacio greške u kod i provjerio da padaju.

Izuzetak upisujem kao dan i sat u Sarajevu, a backend hoće `+01:00` ili `+02:00`. Pomak zavisi od trenutka koji tek računam. Rješenje je probati oba pomaka, od dan prije i od dan poslije, i zadržati onaj koji se slaže sam sa sobom. Ispalo je da to usput riješi i oba rubna slučaja: 28.3.2027. u 02:30 ne prolazi nijedan pomak, a 25.10.2026. u 02:30 prolaze oba.

Backend je za preklapanje intervala pisao "s intervalom rules[0]". Dok nije bilo ekrana, niko tu poruku nije pročitao.

Najduže sam se zadržao na jednoj liniji. Poslije novog resursa aplikacija ide na njegovo radno vrijeme, a ta stranica traži resurs u listi. Izbacio sam osvježavanje liste i test je prošao. Napisao sam test koji pamti svaki naslov koji se pojavi, i on je pao i sa osvježavanjem i bez njega: na tren je pisalo "Radno vrijeme nije dostupno". U izvoru TanStack Query je pisalo zašto. `invalidateQueries` ponovo učitava samo upite koje neko trenutno prikazuje, a na stranici "Novi resurs" listu ne prikazuje niko. `await` se zato završio odmah. Treba `refetchType: 'all'`. Ista greška je bila i kod prihvatanja pozivnice, gdje sam prošli put zapisao da osvježavanje ostavljam "za slučaj kad je lista u kešu". Baš u tom slučaju ne bi ništa uradilo.

Oxlint je javio da čitam ref tokom crtanja. Ref mi je pamtio kojim redom su intervali poslani. Mutacija to već pamti u `variables`, pa ref nije ni trebao.

U Edgeu polje za sat pokazuje `08:00 AM`, jer je browser na engleskom. Šalje i dalje `08:00`.


## srijeda, 18. novembar 2026. u 19:28

Korak 11, statistika. Biblioteku za grafove nisam uzeo, jer su svi grafovi vodoravne trake, a za to je dovoljan `<span>` sa širinom u procentima unutar obične tabele.

Najviše me iznenadio CSV. Da sam fajl čitao sa `text()`, BOM bi nestao, jer `Body.text()` dekodira UTF-8 i baci ga usput. Zato `apiFetch` sad zna vratiti i `blob`. U testu je pukla druga stvar: `new Response(blob)` u jsdom-u ne prepozna jsdom Blob, nego pošalje tekst `[object Blob]`. Kod je bio dobar, test nije.

Test za grešku servera je pao, jer moj lažni backend nije znao da aplikacija sama ponovi upit jednom poslije sekunde. Vratio je uspjeh baš na taj drugi pokušaj, pa se greška nije ni prikazala.

Na telefonu je tabela popunjenosti bila šira od ekrana (487 px na 375). Sati su otišli u drugi red ispod naziva i ostale su dvije kolone.

Jednom, poslije mutacija, palo je 6 testova, iako su fajlovi bili vraćeni bajt po bajt. U sljedeća tri pokretanja sve je prošlo. Ne znam koji su testovi pali, jer sam gledao samo broj. Sljedeći put ću sačuvati izlaz.

U testu je ostao nevidljiv BOM znak u stringu, umjesto `\uFEFF`. Grep ga je našao, ali je commit prošao, jer komanda nije stala na nalazu. Ispravio sam to i dodao u isti commit prije pusha.


## četvrtak, 19. novembar 2026. u 10:32

Prvi put je frontend razgovarao s pravim backendom, i to preko Playwrighta. Oba testa su prošla iz prvog pokušaja, što mi nije bilo drago, jer nisam znao da li uopšte mogu pasti. Zato tri mutacije: sat u zoni browsera, `409` kao obična greška i otkazana rezervacija koja i dalje drži termin u `FreeSlotService`. Svaka je oborila baš onaj test koji treba. Zona browsera se vidi samo zato što je browser u testu namjerno u New Yorku, moj laptop je u istoj zoni kao Sarajevo.

Prvo pokretanje je palo s `'mvnw.cmd' is not recognized as an internal or external command`. Playwright komandu pokreće kroz `cmd`, a `cmd` iz Node-a ne traži program u trenutnom folderu, pa treba `.\mvnw.cmd`. Baza je pritom ostala upaljena, jer se `globalTeardown` ne pokrene kad `webServer` ne starta.

Mislio sam da ću bazu dići u `globalSetup`. U izvoru Playwrighta piše da se `webServer` pali prije `globalSetup`-a, pa bi backend krenuo bez baze. Sad je `docker compose up --wait` u istoj komandi kao backend.

Za `JWT_SECRET` sam napisao rečenicu koja jasno kaže da nije tajna. Lokalni gitleaks ju je svejedno prijavio kao `generic-api-key`, kao `.env.example` u Fazi 1. Ključ se sad pravi nasumično pri svakom pokretanju.

Token pozivnice ide samo mailom, pa test čeka Mailpit do 30 s. To je pola trajanja testa (oko 1,9 min ukupno).

## četvrtak, 19. novembar 2026. u 10:56

Pushao sam sve od 18.11. i krenuo u Fazu 8. Mislio sam da "test kroz sve endpointe s tuđim tokenom" tek treba napisati, a postoji od Faze 2. Gledao je samo admina druge organizacije. Klijent iz iste čitaonice koji čita članove ne bi pao nigdje osim u testu članova, a provider koji uređuje tuđi sto samo na nekim rutama.

Sad istih 25 zahtjeva ide i s Lejlom (klijent) i s Damirom (vodi drugi sto). Za svakog sam rukom napisao šta smije. Sve je prošlo iz prve, pa nisam znao da li test išta vidi. Tek kad sam klijentu dao pravo na članove, test je pao s `[klijent: GET /api/organizations/{id}/members] expected: 403 but was: 200`.

Iznenadila me mutacija u promjeni providera. Kad servis traži resurs bez organizacije iz putanje, Bojan prođe `@PreAuthorize` sa svojom teretanom i dođe do upisa. Tamo ga zaustavi `resources_provider_clan`, a on dobije `500`. Baza je čuvala i tamo gdje sam mislio da čuva samo Java.

Bash heredoc s Java kodom je opet pao s `unexpected EOF`, isto kao u koraku 11. Skriptu pišem u fajl i pokrećem je odatle.

## četvrtak, 19. novembar 2026. u 11:12

Rate limiting za prijavu i registraciju. Bucket4j sam našao u `maven-metadata.xml` na Maven Centralu, jer je pretraga na search.maven.org pokazivala 8.14.0 iz 2024, a zadnja je 8.22.0. Core nema nijednu zavisnost, pa se ne može posvađati sa Spring Bootom 4.1.

Najviše vremena je otišlo na pitanje koja je adresa klijenta. Na Renderu je adresa konekcije Cloudflare, pa bi svi dijelili jednu granicu. `X-Forwarded-For` sam htio uzeti, dok nisam pročitao da ga Cloudflare samo dopuni, pa lijevi dio piše ko god pošalje zahtjev. Na produkciji se zato čita `CF-Connecting-IP`. Na Renderu to nisam probao.

Prvi problem bi bio u testovima, a ne u kodu: svi šalju s 127.0.0.1, pa bi preko 340 testova zajedno potrošilo 20 prijava u minuti. Testovi imaju granicu 100000, a jedan test ima svoj kontekst s granicom 3.

Mutacija zaokruživanja je prošla. Test je čekao pola sekunde, a za pola sekunde i naviše i naniže uz `Math.max(1, ...)` daju 1. Kad sam test popravio, vidio sam i da `toMillis()` 1 ns odsiječe na 0, pa sad računam iz nanosekundi i `Math.max` više ne treba.

## četvrtak, 19. novembar 2026. u 13:55

Granica mailova. Prva ideja je bila Bucket4j kao za prijavu, ali svaki mail ionako završi u tabeli `notifications`, pa sam brojao tamo: zadnja tri maila iste vrste istom primaocu, i ako su sva tri unutar sata, čeka se dok najstariji ne izađe.

Reset lozinke ne smije vratiti `429`. Za nepoznat email nikad ne bi bilo `429`, pa bi se po tome vidjelo ko ima nalog. Zato četvrti zahtjev dobije isti `204`, samo mail ne nastane.

Test za pozivnice je pao s `Status expected:<429> but was:<201>` i prvo nisam znao zašto. Kad nova pozivnica zamijeni staru, V24 kaskadom briše i njen mail, pa je u tabeli uvijek bio samo jedan i granica nikad nije došla do tri. Admin je mogao zvati istu osobu do mile volje. Sad FK ide na `SET NULL`, servis briše samo mail koji još čeka, a constraint ne da da mail koji čeka ostane bez pozivnice. Kad sam u mutaciji izbacio to brisanje, baza je stvarno odbila s `violates check constraint "notifications_primalac"`.

Prvi krug mutacija je javio da je svih osam preživjelo, što je bilo sumnjivo. Maven se nije ni pokrenuo (`'mvnw.cmd' is not recognized`, ista greška kao kod Playwrighta), a skripta je "nema izvještaja" brojala kao "nijedan test nije pao". Sad skripta stane ako nema izvještaja.

Još jedna: test čekanja je pomjerao sva tri maila zajedno, pa ne bi primijetio da kod računa od najnovijeg umjesto od najstarijeg.

## četvrtak, 19. novembar 2026. u 14:34

Zaključavanje prijave. Najvažnija odluka je bila da se broji po emailu iz zahtjeva, a ne po nalogu. Da brojim samo za postojeće naloge, izmišljen email bi peti put dobio `401`, a pravi `429`, i eto liste ko ima nalog. Tabela `login_failures` zato nema strani ključ na `users`.

`INSERT ... ON CONFLICT DO NOTHING` mi je bio nov. Prva greška za email mora napraviti red, a dvije niti mogu to pokušati u istom trenutku. S `ON CONFLICT` druga tiho preskoči, pa onda obje čitaju red sa `FOR UPDATE`. Kad sam u mutaciji izbacio `FOR UPDATE`, od četiri istovremene greške izbrojane su dvije.

`AuthRateLimitApiTest` šalje 13 pogrešnih lozinki za istu Selmu, pa bi je novo zaključavanje zaključalo i pokvarilo test granice po adresi. Taj test sad ima `slotbook.login-lock.failures=1000`.

Napisao sam vraćanje brojača na nulu poslije zaključavanja, pa skontao da ne radi ništa: prozor kreće od prve greške i traje koliko i zaključavanje, pa je uvijek istekao kad zaključavanje prođe. Izbacio sam te dvije linije prije mutacija, jer bi mutacija koja ih briše preživjela.

Ostaje ružna strana: ko zna nečiji email, može ga držati zaključanim pet pogrešnih lozinki svakih 15 minuta.

## četvrtak, 19. novembar 2026. u 17:24

Enumeracija. Plan je bio jednostavan: registracija vraća isti odgovor i kad nalog postoji. Kad sam krenuo, skontao sam da to ništa ne sakriva dok se nepotvrđen nalog može prijaviti. Registrujem tuđi email svojom lozinkom i odmah se prijavim: prođe, nalog nije postojao, `401`, postojao je. Naš frontend tačno to radi. Zato se sad prijavljuje tek poslije potvrde, a to je značilo 19 test klasa koje su registrovale pa se prijavljivale. Sad potvrđuju email direktno u bazi.

Usput je pao `InviteIstovremenoTest`, ali samo u cijelom pokretanju, a sam je prošao tri puta. Greška iz koraka 3: između brisanja maila koji čeka i brisanja pozivnice druga nit commita novu pozivnicu, pa `SET NULL` udari u `notifications_primalac`. Prvo sam to dokazao privremenim testom s dvije JDBC konekcije, pa tek onda popravljao. Mutacija za taj popravak preživi, jer se niti ne poklope svaki put.

Mjerenje vremena me iznenadilo na drugu stranu. Za registraciju sam očekivao problem i lažni BCrypt ga riješi (107 ms naspram 103). Za reset lozinke sam mislio da je sve isto, a postojeći email je sporiji, 27 ms naspram 12, jer upisuje token i mail. Prvo sam test nazvao "traje kao za nepoznat" i prošao je samo zato što je granica bila 25 ms. Preimenovao sam ga u ono što stvarno provjerava.

Heredoc u bashu mi je opet pojeo `\t` i `\n` u Python skripti, pa se skripta za mutacije nije ni pokrenula. Ovaj put je bar pala sa `SyntaxError`, umjesto da javi da je sve preživjelo.

## četvrtak, 19. novembar 2026. u 17:53

Zaglavlja i CORS, kraće nego što sam mislio. Spring Security već šalje `nosniff` i `X-Frame-Options: DENY`, to sam vidio tek kad je test pao na `Referrer-Policy`, a prošao prve dvije provjere.

HSTS me zbunio. Spring ga šalje samo za HTTPS zahtjev, a iza Renderovog proxyja aplikacija vidi običan HTTP, pa ga ne bi poslala nikad. Sad ide na svaki odgovor, browser ga na HTTP-u ionako ignoriše.

Preflight je bez `.cors(...)` dobijao `401`, jer `OPTIONS` nema token. I skontao sam da CORS za glavni tok na produkciji vjerovatno neće ni trebati: refresh kolačić je `SameSite=Strict`, pa frontend na drugom sajtu ionako mora ići kroz proxy. To je za Fazu 12.

## četvrtak, 19. novembar 2026. u 18:18

Audit log. Prvi put sam napisao mutaciju za koju sam htio da preživi: zapis o promjeni providera premješten prije provjere da je novi provider član. Provjera baci `400`, rollback odnese i zapis, i test ne primijeti ništa. Tek tada mi je postalo jasno šta `MANDATORY` stvarno daje. Zapis ne može otići u svoju transakciju i ostati kad radnja padne.

Test izolacije je pao na `expected: 8L but was: 9L`. Mislio sam da sam našao rupu, a u stvari provider po pravilima smije napraviti svoj resurs, i to je legitiman zapis. Pogriješio je test, pa sad provjerava koje su radnje upisane, ne koliko ih ima.

Redoslijed po `created_at` ne bi bio pouzdan, jer se u testu sedam radnji desi skoro u istoj milisekundi. Zato je ID redni broj, a ne UUID.

Python heredoc mi je treći put pojeo `\r\n` u skripti. Ovaj put je bar `ARHITEKTURA.md` bio jedini fajl sa CRLF-om, pa je zamjena pala na `assert` umjesto da išta pokvari.

## četvrtak, 19. novembar 2026. u 18:42

Izvoz podataka, deset tabela u jednom JSON-u. Prvi put sam koristio `JdbcClient` umjesto JPA. Bio sam siguran da `TIMESTAMPTZ` neće sam postati `Instant`, a postao je, sa `Z` kao i ostatak API-ja.

Najviše sam razmišljao šta ne smije izaći. Hash lozinke je očigledan. Tijelo maila mi nije palo na pamet dok nisam vidio da u njemu stoji link s pravim tokenom za reset.

Test je prvi put pao na mojoj brojci, `expected 5 but was 4`. Brojao sam dvije sesije, a prijava je bila jedna. Dvije mutacije bi prošle da test nisam pojačao prije pokretanja: pozivnica po emailu i dnevnik bez uslova.

## subota, 21. novembar 2026. u 11:58

Brisanje naloga. Red iz `users` se ne briše nego anonimizira, i to čuva `CHECK` u bazi, pa obrisan red ne može imati pravi email ni kad ga neko mijenja ručno.

Prvo pokretanje je dalo `500` na svakom uspješnom brisanju: `Can't infer the SQL type to use for an instance of java.time.Instant`. Kod izvoza je `JdbcClient` čitao `Instant` bez problema, pa sam mislio da i šalje. Ne šalje, pa sad ide `OffsetDateTime` u UTC-u.

Onda je pao `login_failures`, `expected 0 but was 1`. Opet sam pogriješio u testu: poslije brisanja se prijavim starim emailom da vidim `401`, a ta prijava upiše novi red. Tako je i namjerno još od koraka 4.

Test s dva admina koji brišu nalog u isto vrijeme je pao čim sam izbacio zaključavanje. To mi je bio prvi `FOR NO KEY UPDATE`, a razliku od `FOR UPDATE` sam našao tek u tabeli konflikata u dokumentaciji.

## subota, 21. novembar 2026. u 12:16

Frontend za Fazu 8. Prvi test za `202` je pao sa `SyntaxError: Unexpected end of JSON input` i tek tad mi je bilo jasno da registracija kroz formu ne radi još od koraka 5. Backend je imao svoje testove, frontend svoje s lažnim `201`, i oboje je bilo zeleno.

Stari test pozivnice je pao jer registracija više ne prijavljuje. Ispod toga je bio pravi problem: link iz maila otvori nov tab i adresa pozivnice se izgubi. Sad link "Na prijavu" nosi adresu, i test to provjerava.

Skripta za screenshotove nije učitala aplikaciju. `page.route('**/api/**')` je hvatao i Viteove module iz `/src/api/`, pa nije stizao JavaScript. Morao sam gledati samo putanje koje počinju sa `/api/`.

Na screenshotu sam vidio "Pokušaj ponovo kasnije. Ponovo možeš pokušati u 12:27." Testovi su to pustili jer su provjeravali samo moj dio rečenice.

## subota, 21. novembar 2026. u 23:59

CI za Fazu 8. Mislio sam da je CodeQL samo još jedan workflow, a na privatnom repou ne radi bez plaćene licence. Licenca za CodeQL CLI ne dozvoljava ni da ga pokrenem sam nad zatvorenim kodom. Repo zato postaje javan, malo prije nego što sam planirao.

Iznenadilo me da Dependabot frontend nikad nije ni gledao. U dependabot.yml je od Faze 1 bio samo Maven.

Prije pusha sam Vitest pustio sa TZ=UTC, jer CI nije u Sarajevu. Prošlo je, ali bez toga ne bih znao da li testovi zavise od zone mog računara.

## nedjelja, 22. novembar 2026. u 00:15

Repo je javan. CodeQL je odmah pao, ali ne zbog koda nego zbog postavki: uključio sam default setup, a on ne prima rezultate iz našeg workflowa. Poruka je bila jasna, samo sam je morao naći u anotacijama joba.

Dependabot je za par minuta otvorio sedam PR-ova. Nisam ih mergao, prvo ih treba proći s testovima.

Za OWASP prolaz sam pazio da uz svaku stavku stoji ime testa. Najviše mi je smetao A09: audit log postoji, ali alarma nema nikakvog, i zaključane prijave vidi samo log na Renderu koji niko ne čita.

Faza 8 je gotova.

## nedjelja, 22. novembar 2026. u 00:31

Počela Faza 9, prvi korak je ID zahtjeva u logu. Mislio sam da za JSON logove treba logstash encoder i `logback.xml`, a Spring Boot to ima ugrađeno, jedno svojstvo.

Prvo pokretanje mutacija je reklo da je svih šest preživjelo. Testovi nisu bili krivi: skripta je zvala `mvnw.cmd` bez putanje, cmd ga nije našao (`'mvnw.cmd' is not recognized`), i ništa se nije pokrenulo. Skripta je "nema pada" čitala kao "test nije uhvatio".

U testu sam čitao `log.level` kao jedan ključ, a Spring ga piše ugniježđeno, `"log":{"level":"ERROR"}`. Taj test je onda sam prolazio, a u cijeloj suiti pao sa `Unexpected character ('-' (code 45)): Expected space separating root-level values`. Linija nije bila JSON nego običan tekst koji počinje datumom. Spring podesi Logback iznova kad krene svaki kontekst, a moj kontekst je bio keširan i ponovo korišten poslije drugih. Test sada ne provjerava format, samo ID u liniji.

Prije toga sam podigao zavisnosti iz sedam Dependabot PR-ova i pustio obje suite: 404 i 188, sve prolazi.

Na kraju je ID ušao i u tijelo greške `500` i u CORS `exposedHeaders`. Oba testa sam prvo vidio crvena: `No results for path: $['requestId']` i `Expected: a string containing "X-Request-Id"`.

## nedjelja, 22. novembar 2026. u 11:26

Ostatak Faze 9 u jednom komadu, korak 2 do 8.

Readiness i liveness su u Bootu 4.1 već bili uključeni, test je prošao prije ijedne linije koda. Pao je samo dio da readiness gleda bazu. Kad sam iz grupe izbacio `readinessState`, readiness je vraćao `200` i kad aplikacija objavi `REFUSING_TRAFFIC`.

Kod Sentryja sam pretpostavio da starter sam hvata greške kontrolera. Ne hvata, jer naš `GlobalExceptionHandler` obradi svaki izuzetak prije njegovog resolvera. Rješenje je `sentry-logback`, i tada ERROR linija postaje događaj. Prvi test je pao baš na tagu: `expected: "ea29604c-..." but was: null`, dok nisam dodao `sentry.context-tags=requestId`. Uslov za uključivanje je samo postojanje `sentry.dsn`, pa bi `${SENTRY_DSN:}` uključio Sentry s praznim DSN-om. Zato DSN nije u properties fajlu, Spring ga čita iz varijable.

N+1 nisam našao nigdje. Brojač upita sam stavio kao `StatementInspector` u `ThreadLocal`, jer bi globalna Hibernate statistika brojala i `BookingJob`. Svoj test sam prvo napisao pogrešno: raspored sa `from` i `to` isti dan je vratio `400`, jer je `to` isključen. Prava stvar je izašla iz `EXPLAIN`: raspored je za sedmicu čitao 1 861 red da vrati 33, jer indeks može suziti samo `start_time < kraj`. Donja granica od 8 sati i `CHECK` u `V33` su to spustili na 33. U ODLUKE sam još ispravio raniji zapis: upit za zauzete termine ipak koristi gist indeks, samo po `resource_id`.

k6 sam pustio iz Dockera, jer nije instaliran. Tri pokretanja, svaki put 601 rezervacija i u sudaru 30 puta `201` i 570 puta `409`, bez ijednog 5xx. Iznenadilo me koliko je log bučan: svaki `409` ostavi dvije WARN linije Hibernatea, 1 140 linija po pokretanju.

Za backup mi je Git Bash prepravio `/tmp/slotbook.dump` u Windows putanju, pa je `pg_dump` pao s `could not open output file "C:/Users/HP/AppData/Local/Temp/slotbook.dump"`. Proradilo je sa `MSYS_NO_PATHCONV=1`. Vraćena baza ima isti broj redova u svih 15 tabela, a aplikacija se nad njom podigla bez ijedne migracije. Na Neon-u to tek trebam uraditi.

Kasnije isti dan: Sentry je povezan, Render gleda readiness, sve je pushano i CI je zelen. Backup s Neon-a je prvo izgledao gotov, jer je dump od 49 KB nastao za 8 sekundi. Onda je `pg_restore` izbacio 62 greške, sve zbog `unrecognized parameter "pg_attver"`. Neon u dump upisuje svoju opciju za kolone, a običan Postgres ne zna šta je to. Prošlo je tek kad sam dump pretvorio u SQL i te linije izbacio sa `sed`. Da nisam poredio broj redova, mislio bih da imam backup.


## nedjelja, 22. novembar 2026. u 16:09

QR check-in, korak 6 Faze 10. Sesija mi se prekinula usred koraka, pa je nastavak išao od napola napisanog backenda.

Kod u QR-u nije u bazi. To je HMAC nad ID-jem rezervacije, s ključem izvedenim iz JWT tajne, i backend ga samo izračuna ponovo i uporedi. Za poređenje ide `MessageDigest.isEqual`, jer obično `equals` stane na prvom različitom znaku, pa bi se iz trajanja moglo naslutiti koliko je pogođeno.

Prvo je link bio `/check-in/{id}?k=...`. Dok sam pisao stranicu za providera, vidio sam da `Cuvar` pri slanju na prijavu pamti `pathname + hash`, a query ne. Provider koji nije prijavljen bi se prijavio i ostao bez koda. Prebacio sam na `#k=`, kao pozivnica, i to sada provjerava Vitest test s prijavom.

E2e test je prvi put pao poslije 90 sekundi: `waiting for getByRole('button', { name: /11:00 do 12:00, slobodno$/ })`. Kod nije bio kriv. Test za `409` već uzima 11:00 i 12:00 u istoj organizaciji, a ja sam izabrao termin bez gledanja šta drugi testovi drže. Sad je 13:00. Poslije toga sam ga greškom pustio još jednom cijelog, umjesto da samo pročitam izvještaj, pa je to bilo još četiri minute.

Mutacije su ovaj put uhvatile obje stvari: bez provjere koda pada test s kodom druge rezervacije, a `canView` umjesto `canManage` pada test u kojem studentica sama sebe skenira. Test izolacije tu drugu mutaciju nije uhvatio, i to je u redu, jer Lejla i Damir ni s `canView` ne vide Alminu rezervaciju.

Da stvarna kamera pročita kod s ekrana nisam provjerio. Telefon ne može otvoriti `localhost`, pa to čeka Vercel.

## nedjelja, 22. novembar 2026. u 17:02

Kapacitet veći od 1, i s tim kraj Faze 10.

U planu je pisalo da `EXCLUDE` za grupni termin ne vrijedi i da broj mjesta čuva Java. Izabrao sam drugi put: svaka rezervacija dobije broj mjesta, a constraint kaže da se isto mjesto ne preklapa. Za običan resurs se ništa ne mijenja, jer su sve njegove rezervacije na mjestu 1. Ono što baza i dalje ne zna je da mjesto ne smije biti veće od kapaciteta. To sam vidio na mutaciji: kad sam Javi dozvolio deset mjesta više, test sa 20 niti je umjesto 5 pustio 15, i baza nije rekla ništa.

Prvo pokretanje je palo na svim testovima odjednom: `Schema validation: wrong column type encountered in column [seat] in table [bookings]; found [int2 (Types#SMALLINT)], but expecting [integer (Types#INTEGER)]`. Stavio sam `SMALLINT` jer je broj mali, a Hibernate za Java `int` traži `INTEGER`. Migracija nije bila izvršena nigdje osim u Testcontainers, pa sam je smio ispraviti.

Najviše posla nije bilo u rezervaciji, nego u svemu što je podrazumijevalo da je termin ili slobodan ili zauzet: kalendar, lista čekanja, posao koji nudi oslobođen termin, serije, pa i statistika. Popunjenost bi za punu salu s 5 mjesta pokazala 500 %. To nijedan test nije tražio, vidio sam to tek kad sam tražio ko još koristi ponuđene minute.

Kod mutacija sam jednom zaključio prebrzo. Dvije mutacije u istom pokretanju su oborile isti test, pa nisam znao koja ga je oborila. Drugu sam pustio samu i tek onda je bilo jasno da i SQL uslov u poslu ima test.

E2e za grupnu sobu ne pokazuje "popunjeno", jer u testu postoje samo dvije osobe, admin i klijent, a za pun termin s dva mjesta treba treća koja nije ni jedna od njih. To pokrivaju Vitest i backend.

## nedjelja, 22. novembar 2026. u 17:36

Mislio sam da je CI zelen poslije Faze 10, a nije bio. Gledao sam Render, koji je bio zelen, i listu u Actions, gdje su CodeQL i CI za isti commit jedan ispod drugog. Crveni X je bio na CI-ju. Pao je frontend test za QR kod: `Unable to find role="alert"`. Aplikacija na 503 ponovi upit poslije 1000 ms, a `findByRole` čeka isto toliko. Na mom laptopu je stizalo, na GitHubu nije. Svi ostali testovi za 503 već imaju `timeout: 3000`, samo ovaj nije.

Počela Faza 11, plaćanje. Razbili smo je na pet koraka. Prvi je samo cijena na resursu, bez ikakvog Stripea. Cijena je u feninzima, cijeli broj, jer Stripe tako i računa. U formi se upisuje u KM sa zarezom, a ono što me iznenadilo je `0.29 * 100`, što u JavaScriptu nije 29 nego `28.999999999999996`. Zato se cijeli dio i feninzi računaju posebno.

Druga stvar koju ne bih sam primijetio: `Number('15 KM')` je `NaN`, a `JSON.stringify` od `NaN` je `null`. Backend za `null` uzima 0, pa bi pogrešno upisana cijena tiho napravila besplatan termin. Forma sad takav unos ne šalje.

Stripe nalog ostavljam za sutra.

## utorak, 24. novembar 2026. u 15:23

Dug dan, Faza 11 od koraka 2 do 5. Plan je bio cijela faza za sat, a ispalo je cijeli dan, i to bez ručne provjere sa Stripe CLI, koja i dalje čeka mene.

Stripe nalog sam napravio i dobio sandbox u Njemačkoj, jer BiH nije na listi. KM radi: prava test sesija se otvorila za 15 KM u `bam`. Rok za uplatu je 30 minuta, a ne 15 kako sam htio, jer Checkout sesija ne može trajati kraće. U commitu iz koraka 2a1 i dalje piše 15, a to ostaje, jer je već pushano.

Greške redom. `currency CHAR(3)` je oborio sve testove: `found [bpchar], but expecting [varchar(255)]`, isto kao sa `SMALLINT` u Fazi 10. Nisam naučio prvi put. Pa `gson` iz stripe-java nije na classpathu za kompajliranje, samo za runtime, i `catch (JsonParseException)` se nije kompajlirao. Pa constraint iz V18, koji kaže da rezervaciju bez aktera može promijeniti samo istek. Potvrda od Stripea nema aktera. Nisam oslabio constraint, nego je akter klijent, jer je on platio.

Najbolja stvar dana je test s pet istih webhookova u istom trenutku. Prva verzija je vratila novac četiri puta za ispravno plaćen termin. Uplatu sam čitao prije nego što sam zaključao rezervaciju, pa su sve niti vidjele uplatu kao otvorenu, a onda svaka sljedeća poslije lock-a mislila da je rezervacija već potvrđena "nekom drugom" uplatom. Sad se prvo zaključa rezervacija, pa se tek onda čita uplata.

Kod mutacija sam opet stavio dvije u isto pokretanje i jedna je sakrila drugu. Jednom je vraćen fajl imao stari `mtime`, pa je Maven koristio izmijenjenu klasu i "pala" su mi dva testa koja nisu imala veze s ničim.

U koraku 4 sam napisao komentar da bi izuzetak iz `AFTER_COMMIT` slušaoca klijentu dao 500. Mutacija je pokazala da ne bi: Spring ga sam uhvati i samo zapiše `TransactionSynchronization.afterCompletion threw exception`. Komentar je bio pogrešan, ne kod.

U koraku 5 je kod nastao prije testova, pa je svaki test morao pasti na mutaciji (11 u frontendu, 2 u backendu). Za screenshotove je Playwright ruta `**/api/**` hvatala i `/src/api/statistika.js` iz Vitea, pa je aplikacija ostala prazna 30 sekundi dok nisam vidio šta se traži. Dvaput je i pomoćni fajl završio na Desktopu, van repoa, umjesto u privremenom folderu.

Poslije commita sam instalirao Stripe CLI (winget, 1.55.14) i prijavio se u sandbox. Mislio sam da je jutarnja test uplata prošla, a nije: sesija za 15 KM je istekla neplaćena tačno poslije 31 minute, i u sandboxu nema nijednog payment intenta, znači forma za karticu nikad nije poslana. Bar je usput potvrđeno da Stripe stvarno zatvori sesiju na našem `expires_at`.

Backend iz terminala nije htio krenuti: `Port 8080 was already in use`. Port je držao stari backend iz IntelliJ-a, pokrenut u 14:31, još s kodom od prije webhooka. Kad sam sve pokrenuo, mail za novi nalog nije stigao. To još nisam istražio.

## srijeda, 25. novembar 2026. u 13:47

Jutro je bilo za Stripe CLI. `stripe listen --forward-to localhost:8080/api/stripe/webhook` je vratio `must specify events to forward using --events, --all-snapshot, or --all-thin`, jer je komanda bila napisana po sjećanju. Treba `--events checkout.session.completed,checkout.session.expired`. Drugo iznenađenje: `whsec_` koji listen ispiše nije isti kao onaj u `.env`, pa sam ga morao zalijepiti i ponovo pokrenuti backend. Ovaj put je mail za novi nalog stigao, uz Docker koji radi. Da je jučer Docker bio ugašen samo pretpostavljam. Uplata 15 KM karticom 4242 je potvrdila rezervaciju, a otkazivanje je vratilo novac i poslalo mail.

Onda redizajn, prije deploya. Aplikacija je izgledala kao forma u lijevom uglu na krem papiru, a krem je baš ona boja s liste znakova AI dizajna. Izabrao sam "Čelik i žar" i kartu za voz kao potpis. Colorhunt i Dribbble se ne daju pročitati običnim fetchom, jer sve crta JavaScript, pa sam ih gledao kroz Edge preko Playwrighta.

Najviše me iznenadila greška koju je redizajn otkrio, a nije od njega. Poslije rezervacije fokus je trebao ići na poruku "Termin je tvoj", a išao je na `body`. Poruka je imala `:empty { display: none }`, a `focus()` se zove prije nego React upiše tekst, kad je element još sakriven. Vitest je to sve vrijeme puštao, jer jsdom ne zna za CSS. Vidio sam tek kad sam u Edge-u ispisao `document.activeElement`.

jsdom je zezao i na drugom mjestu: `getByRole('figure', { name: /Primjer karte/ })` ne nađe ništa, jer ne računa ime figure iz `figcaption`. Browser ga računa.

E2e je prvi put pao na tekstu `Sto 4, tihi dio · Čitaonica PMF`. Red u Mojim rezervacijama je postao karta, a Vitest testove sam promijenio, e2e nisam ni pogledao. Prije toga se e2e nije htio ni pokrenuti, jer je port 5173 držao Vite proces koji je preživio gašenje taska. Moralo se tražiti ko drži port, ne pretpostaviti.

Sitnica koja je pojela vrijeme: fajlovi u repou su mješavina CRLF i LF, pa zamjena teksta u `forma.css` nije našla ništa dok skripta nije pazila na kraj reda. Od toga je u `tokeni.css` ostala dupla linija `--ivica-dugmeta`, koju sam našao tek dva koraka kasnije.

## srijeda, 25. novembar 2026. u 14:12

Poslije pusha je došao demo. Mislio sam da je "noćni reset" iz plana jedna `@Scheduled(cron = ...)` linija, ali Render na besplatnom planu spava čim nema zahtjeva, a s njim spava i raspored. Posao u 4 ujutro bi se vrtio samo kad neko slučajno gleda demo u 4 ujutro. Sad se pri svakom startu i svaki sat pita: jesu li demo računi stariji od dana, i postoje li uopšte. Drugo pitanje je došlo kad sam shvatio da posjetilac može obrisati Hanin nalog i pokvariti demo svima do sutra.

Prvo sam u upitu imao i `deleted_at is null`, za obrisane naloge. Onda sam pogledao `AccountAnonymization`: obrisan nalog dobije email `obrisan-...@slotbook.invalid`, pa ga upit po emailu ionako ne nađe. Uslov je bio višak i izbačen je.

Demo podaci idu SQL-om, ne kroz servise, jer `BookingService` ne pušta rezervaciju u prošlosti, a statistika bez prošlosti je prazna. Bio sam nervozan da ću upisati nešto što aplikacija nikad ne bi napravila, ali `EXCLUDE` i `CHECK` važe i za `JdbcTemplate`. Prvo punjenje je prošlo bez ijednog constrainta, što me malo iznenadilo.

Svaki test u `DemoApiTest` na početku "postari" demo za dva dana, da bi provjera morala napuniti. Mutacija bez pravila od 24 sata je zato oborila svih šest testova, a ne samo onaj koji to dokazuje. Radi, ali bi bilo ljepše da pada jedan.

Dokumentacija za Vercel kaže da rewrite prosljeđuje IP posjetioca u `x-forwarded-for`, a nama granica pokušaja čita `CF-Connecting-IP`, gdje će biti Vercelov server. Ne znam još šta stvarno stigne na Render. Ostavio sam kako jeste i zapisao kao otvoreno.

Opet je poslije gašenja taska ostao živ backend na 8080 i Vite na 5173. Ovaj put sam odmah tražio ko drži port. README je pisao 466 backend testova od 22.11., a ima ih 505: Faza 11 je dodala testove, a brojka nije ažurirana.

## srijeda, 25. novembar 2026. u 19:45

Vercel se digao iz prvog puta, za minutu. Više vremena je otišlo na Render, koji se poslije pauze budio 207 sekundi, a README je pisao "oko minutu".

Granica pokušaja je ispala gore nego što smo mislili. Mislio sam da će svi posjetioci kroz Vercel dijeliti jednu granicu, a desilo se suprotno: 30 pogrešnih prijava u minuti, nijedna `429`. Vercel šalje s više adresa, pa se zahtjevi raspu po bucketima. Prvi test je čak pokazao da granica radi, ali samo zato što je svih 25 zahtjeva išlo na isti email, pa je posle pet proradilo zaključavanje naloga, koje isto vraća `429`. Dva različita razloga, isti status. Drugi test je svaki put slao drugi email.

Za mail je plan bio port 465. Prije podešavanja smo pročitali stranicu o besplatnom planu na Renderu: od septembra 2025 blokira 25, 465 i 587. Brevo prima i 2525, pa je dodan STARTTLS u `application-prod.properties`. `smtps` ne, jer timeouti važe za `mail.smtp.*`, a `smtps` čita `mail.smtps.*`.

Prvi mail nije ni krenuo. U `MAIL_FROM` sam upisao `SlotBook adresa@gmail.com`, bez `< >`, i dobio od Sentryja `IllegalStateException: Mail za obavijest ... se ne može sastaviti`. Smiješno je da je to prvi pravi događaj koji je ikad stigao u Sentry, tri dana poslije povezivanja. Sa zagradama mail stiže, link vodi na Vercel i prijava radi. Samo završi u spamu, jer šalje Brevo, a pošiljalac je Gmail.

## srijeda, 25. novembar 2026. u 20:28

Redizajn ispočetka, ovaj put bez prototipova. Smjer je Mjed i emajl: termin je žeton, rezervacija je karta s mjedenom folijom. Landing i kalendar su gotovi.

Na prvom screenshotu landinga naslov "Termin je tvoj." se proširio preko ploče s terminima. Slova se šire prema kursoru, a ja sam ih pustio do 148 % širine na fontu od 184 px. Smanjio sam font i dodao `overflow-x: clip` na kolonu s tekstom.

Folija na karti je pravila tanak šav preko kupona. Prvo sam mislio da je kriv `soft-light` preko poluprozirnog kupona i skratio sam foliju samo na glavni dio. Šav je ostao. Pravi uzrok je bilo ponavljanje pozadine: gradijent od 260 % se pomjerao do 120 %, pa se na rubu vidio početak sljedeće kopije. `background-repeat: no-repeat` i pomak do 80 % su ga riješili.

"Sačuvaj kartu kao sliku" je prvi put dao PNG na kojem je karta odsječena i prozirna. `html-to-image` prepisuje izračunate stilove u kopiju, pa se u kopiji ponovo pokrenula animacija štampe (`clip-path` od nule), a pozadina sa SVG zrnom se nije prenijela. Dok se pravi slika, karta sad dobije `data-slika`, koji gasi animaciju i zrno.

U kalendaru žeton preleti u kartu preko View Transitions, a ne preko motion `layoutId` kao na landingu. Motion bi u glavni bundle donio oko 50 kB, a ovako je kalendar narastao 2 kB. Zamka: ime prelaza mora biti jedinstveno u starom izgledu, a stara karta ga već nosi kad se klikne drugi žeton. Zato se staroj karti ime ugasi prije prelaza.

Sedam testova u `App.test.jsx` je palo jer `/` za odjavljene više nije prijava nego landing. Testovi prijave sad otvaraju `/prijava`, a test odjave provjerava da se vidi landing i da sesije nema. jsdom nema `matchMedia` ni `ResizeObserver`, pa je dodan `src/test/postavka.js`.

## srijeda, 25. novembar 2026. u 21:07

Ostatak redizajna: moje rezervacije, statistika, prijava, organizacije, nalog, članovi, forme i prelaz između ekrana. Svaki ekran je dobio svoj commit.

Na kraju je jedan test u `App.test.jsx` pao, pa prošao, pa opet pao. "Unable to find role="link" and name "Napravi nalog"", a u `<main>` je stajalo samo "Učitava se". Landing je lijeno učitan, a test registracije je i dalje otvarao `/`. Kad se pokrene sam, landing se učita za manje od sekunde. Uz cijeli suite treba duže, a `findByRole` čeka tačno sekundu. Greška je iz landing commita: testove prijave sam tada prebacio na `/prijava`, a ovaj sam previdio. Sad i on kreće s `/prijava`, a dva testa koja baš provjeravaju landing čekaju do 5 s.

Skripta za screenshotove je stala na `waitForLoadState('networkidle')`: kalendar drži SSE vezu otvorenom, pa mreža nikad nije mirna.

Za prelaz između ekrana sam mislio da je dovoljno dodati `viewTransition` na `Link`. U izvoru React Routera 8.5 se vidi da to radi samo uz `RouterProvider`, a mi imamo `BrowserRouter`. Napravio sam `PrelazLink` od petnaest linija. Ime prelaza ne smije biti na tekstu koji se prelomi u dva reda, zato naslov dobija `inline-block`.

Dok sam pisao o fokusu u `DIZAJN.md`, izračunao sam kontrast umjesto da prepišem stari: svijetli okvir na mjedenom žetonu noću ima 1,69:1. Vidi se samo zato što je odmaknut 3 px, pa stoji na ploči, a ne na mjedi.

Naslov "Termin je tvoj." u prijavi je imao jedan preliv preko oba reda, pa je mjed počinjala na donjem rubu riječi "Termin" i izgledalo je kao greška u štampi. Sad je svaki red obojen posebno. Iznad "Novi izuzetak" je bio višak razmaka, jer je tamo `h3`, a pravilo je hvatalo samo `h2`.

Novi GIF je prvi put imao narandžaste mrlje na mjedenim žetonima. Skripta od prvog redizajna pravi jednu paletu od 64 boje za sve okvire, da žar bude isti u svakom. Mjed, četiri emajla i tinta u to ne stanu, pa su se šipak i mjed stopili u istu narandžastu. Sad svaki okvir ima svoju paletu od 256 boja. GIF je zato narastao s 214 kB na 2,7 MB.

Dva puta isti dan mi je putanja `/c/Users/...` iz Git Basha završila kao `C:\c\Users\...`, jer je Windows Python ne razumije kao bash. Za Python iz Basha pišem `C:/Users/...`.

## Srijeda, 25. novembar 2026, 21:42

Treći redizajn, u jednom potezu. Izbacio sam Anybody i stavio Hanken Grotesk svuda. Mislio sam da će naslovi bez širokog fonta izgledati prazno, a izgledaju kao proizvod, ne kao plakat.

Skripta koja je Sudar.jsx prepravljala tražila je prvi `return (` u fajlu i našla onaj unutar useEffecta, pa je pojela pola komponente. Oxlint je javio "Expected ) but found EOF". Vratio sam izrezani dio iz git show HEAD.

Kad sam skratio "Vrijeme termina je po zoni Europe/Sarajevo" u "Vrijeme: Sarajevo", pala su dva testa koja su tražila /Europe\/Sarajevo/. Testovi nisu bili pogrešni, tekst se namjerno promijenio, pa sad traže novi tekst i i dalje provjeravaju da se zona vidi.

U tamnoj temi je sekcija "Kako radi" nestala, jer je "tamna sekcija" na tamnoj stranici ista boja kao sve oko nje. Vidio sam to tek na screenshotu.

## Srijeda, 25. novembar 2026, 22:27

Dizajn je bio gotov, a CI na backendu crven, iako backend nisam dirao od prošlog zelenog pusha. `gh` nemam, ali repo je javan, pa sam preko `api.github.com` vidio da je pao samo korak "Build i testovi". Logove GitHub ne da bez prijave, pa sam pokrenuo testove lokalno i isti test je pao: `DemoApiTest`, `ERROR: deadlock detected` na `TRUNCATE`.

Mislio sam da je nestabilan test. Nije. Postgres je u poruci napisao ko koga čeka: TRUNCATE čeka tabelu koju drži neki posao, a posao čeka tabelu koju je TRUNCATE već uzeo. Isto se može desiti na Renderu, jer demo tamo svaki sat briše sve dok `@Scheduled` poslovi rade.

Popravka je ponoviti cijelu transakciju, najviše tri puta. Prvo sam htio samo staviti petlju u metodu s `@Transactional`, ali poziv iz iste klase ne prolazi kroz proxy, pa ne bi bilo nove transakcije. Zato `TransactionTemplate`.

Test sam prvo napisao bez popravke i pao je iz pravog razloga. Iznenadilo me da `pg_tables` vraća tabele drugim redom poslije svakog TRUNCATE-a, pa je ista skripta nekad pravila sudar, a nekad ne. Sad je `order by tablename`.

## Četvrtak, 26. novembar 2026, 12:04

Dizajn mi se sviđao, ali paleta je djelovala staro. Porculan `#E7EAF1` i teget noć `#121735` su uz mjed i emajl davali previše plave, a mjed na sivo-plavoj je izgledala kao senf. Pogledali smo šta danas rade slični proizvodi i svuda je isto: topla skoro bijela podloga, noć bez boje, jedna jaka boja i tanka ivica oko ploča. Sad je dan kamen `#F4F2EE`, noć grafit `#0D0E11`, a mjed `#E6A93F`.

Promjena je bila skoro samo u `tokeni.css`. Imena varijabli su ostala, pa se cijela aplikacija prefarbala bez diranja ijedne komponente. Test koji poredi tokene s tabelom u `DIZAJN.md` je natjerao da i dokument ostane tačan, a kontraste sam ponovo izračunao skriptom, ne prepisao stare.

Iznenadilo me nešto u tamnoj traci "Kako radi". U njoj sam prebacio `--tekst` na noćnu vrijednost, ali `--linija` i `--dubina-1` su ostale dnevne. Custom property s `var()` unutra se izračuna tamo gdje je napisan (`:root`), a djeca naslijede gotov rezultat, ne formulu. Zato sam ih u traci morao napisati ponovo. Tamna ploča u prijavi ima isti problem sa sjenama, samo se tamo ne vidi.

Poslije sam dodao efekte na landing: reflektor na karticama, izranjanje pri skrolu, liniju napretka i odsjaj na mjedenom dugmetu. Skrol ide preko `animation-timeline: view()`, bez ijedne linije JavaScripta. Prvo bih bio animirao `transform`, ali scroll animacija s `fill: both` drži svoj `transform` i pregazi hover kartice, pa se kartica ne bi dizala. Zato `translate` i `scale`, koji su posebna svojstva i slažu se s `transform`. Test za reflektor je prvo pao s `document is not defined`, jer fajl nije imao `// @vitest-environment jsdom` kao ostali.

Treći krug istog dana: tamna tema je sad podrazumijevana za sve. Mislio sam da ću trebati JavaScript, a bilo je dovoljno okrenuti selektor u `tokeni.css` na `:root:not([data-tema='svijetla'])` i izbaciti `prefers-color-scheme`. Slobodni termini su dobili mjedeni rub umjesto zelenog, a zauzeti katanac umjesto precrtanog vremena, jer se precrtano teško čitalo. Ispod "Kako je napravljeno" je bilo oko 200 px praznog: 96 od `main`, 48 od landinga i 64 od same sekcije. Za fontove se ispostavilo da se učitavaju samo dva, a Archivo je samo visio u `package.json`.

Screenshot cijele stranice je pokazao praznu sredinu, kao da su sve sekcije nestale. Nisu: Playwright za `fullPage` razvuče prozor na visinu cijele stranice, pa skrol animacije misle da je sve još ispod ekrana. U običnom prozoru, sa skrolom, sve je na mjestu.

## Četvrtak, 26. novembar 2026, 13:19

Spisak od dvanaest sitnica za UI, bez diranja backenda. Prvo je na karti sitni IBM Plex Mono zamijenjen glavnim fontom, a ime mjesta je podebljano. Mono je sad samo za kod i demo lozinke, pa se token zove `--font-kod`, ne `--font-karta`.

Meni na telefonu je `popover="auto"`: Escape i klik van menija browser rješava sam, bez ijednog listenera. U testu ga Testing Library nije nalazila, jer jsdom zatvoren popover smatra skrivenim. Treba `{ hidden: true }`.

Kad sam dodao put do ekrana ("Organizacije / Čitaonica PMF"), pao je test koji je uzimao prvu listu na stranici. Prva lista su sad mrvice. Prvo sam probao uzeti listu van `nav`, ali resursi u tom trenutku još nisu stigli, pa je bila `undefined`. Sad test čeka naziv resursa i uzima njegov `li`.

Najviše me iznenadilo predučitavanje landinga. Dodao sam `import()` na vrhu `App.jsx` kad je adresa `/`, i odjednom je šest testova po ugao palo na isteku od 5 s. Svaki test je učitavao cijeli landing s motionom. Sad se to radi samo u buildu (`import.meta.env.PROD`).

Na lapisu tinta ima 3,72:1. To je ispod 4,5, pa su inicijali na pločici 20 px podebljano, jer veliki tekst traži samo 3:1.
