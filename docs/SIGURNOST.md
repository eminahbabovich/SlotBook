# Sigurnost

OWASP Top 10:2025 (top10.owasp.org/2025), stavku po stavku: šta nas štiti, koji test to dokazuje i šta nije pokriveno. Testovi su klase iz `backend/src/test` i `frontend/src`, pa se svaka tvrdnja može pokrenuti. Pisano je na kraju Faze 8 (22.11.2026), a dopunjeno 26.11.2026, kad sam zaglavlja i CORS provjerio na produkciji.

Produkciju nisam napadao ni skenirao (nema pentesta ni DAST alata). Šta jeste i šta nije provjereno na živoj adresi, piše na kraju.

## A01 Broken Access Control

Svaki endpoint nad podacima organizacije traži članstvo s ulogom, kroz `@PreAuthorize` i klase `OrganizationAccess`, `ResourceAccess` i `BookingAccess`. Svaki upit je ograničen na organizaciju iz putanje, pa tuđi ID daje `404`, a ne tuđe podatke. ID-jevi su UUID, da se susjedni ne može pogoditi (`docs/ODLUKE.md`).

Dokaz je `OrganizationIsolationApiTest`. Prolazi kroz sve rute nad podacima organizacije s tokenom admina druge organizacije, klijenta, providera drugog resursa iz iste organizacije i bez tokena. Ima i čuvara: test pada ako se pojavi nova `/api` ruta koja nije ni na listi organizacije ni na listi `NISU_PODACI_ORGANIZACIJE` s razlogom. Pojedinačna pravila pokrivaju `OrganizationAccessTest`, `ResourceAccessTest` i `BookingPrelaziApiTest` (klijent ne može potvrditi svoju rezervaciju).

Nije pokriveno: access token važi 15 minuta i ne opoziva se. Obrisan nalog ili nalog s promijenjenom lozinkom ga može koristiti do isteka. Članstava više nema, ali novu organizaciju bi mogao napraviti.

## A02 Security Misconfiguration

Šema ide samo kroz Flyway, a Hibernate je na `validate`. `open-in-view` je isključen. Od Actuatora je javan samo `health`. Backend šalje `Referrer-Policy: no-referrer`, HSTS na svaki odgovor (TLS završava Render, pa aplikacija sama ne vidi HTTPS), a na `/api/**` i `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`. CORS pušta samo adresu frontenda iz `slotbook.app-url`. Tajne su samo u varijablama okruženja, a u repou je `.env.example` s lažnim vrijednostima.

Dokaz: `SigurnosnaZaglavljaApiTest` (zaglavlja i na grešci iz filtera, preflight s tuđe adrese odbijen), `SecurityApiTest` (bez prijave se ne vidi koje adrese postoje, `health` je otvoren), `ProdProfilTest`.

Nije pokriveno: Swagger UI i `/v3/api-docs` su javni i u produkciji. To je namjerno, jer je ovo portfolio i opis API-ja nije tajna, ali napadač tako dobija spisak svih ruta. Stranice frontenda na Vercelu nemaju CSP (vidi kraj fajla).

## A03 Software Supply Chain Failures

Dependabot sedmično gleda Maven, npm i GitHub Actions. Na pull requestu Dependency Review obara CI za ranjivost ozbiljnosti `high` i više. Verzije su zaključane u `package-lock.json` i Maven wrapperu, a CI instalira sa `npm ci`. Gitleaks se u CI-ju pokreće iz Docker slike zakucane na verziju.

Dokaz: job `zavisnosti` u `.github/workflows/ci.yml` i Dependabot upozorenja u GitHubu. Prvi dan poslije uključivanja je Dependabot otvorio 7 PR-ova za podizanje verzija. Na šest je Dependency Review prošao, a na sedmom (Maven wrapper) se job nije pokrenuo.

Nije pokriveno: akcije u workflowima su zakucane na glavnu verziju (`@v7`), ne na SHA commita, pa bi kompromitovana akcija ušla bez pitanja. Docker slike su na tagu, ne na digestu. Dependency Review radi samo na pull requestu, a ja guram direktno na `main`, pa za moje commitove ostaju samo Dependabot upozorenja poslije pusha.

## A04 Cryptographic Failures

Lozinke su BCrypt hash snage 10 (`RegisterApiTest.lozinkaSeUBaziCuvaKaoBcryptHash`, `BcryptSnagaTest`). Refresh tokeni i linkovi iz maila su 32 nasumična bajta iz `SecureRandom`, a u bazi je samo njihov SHA-256 (`RefreshApiTest.uBaziJeSamoHashTokena`, `RandomTokenTest`). Refresh kolačić je `HttpOnly`, `Secure` i `SameSite=Strict` (`RefreshApiTest.prijavaPostavljaKolacicKojiJavaScriptNeMozeProcitati`). JWT je HS256 s ključem iz `JWT_SECRET`.

Nije pokriveno: kod ne provjerava dužinu `JWT_SECRET` pri startu. `.env.example` traži najmanje 32 bajta, ali kratak ključ bi aplikacija prihvatila bez upozorenja. Rotacija JWT ključa ne postoji: promjena ključa odjavljuje sve.

## A05 Injection

Svi upiti idu kroz JPA ili `JdbcClient` s parametrima. Na 55 mjesta s `@Query` ili `.sql(` nema nijednog spajanja stringa s podatkom (provjereno pretragom koda 22.11.2026). U `@Query` to ni ne može, jer anotacija prima samo konstantu. React escapea sve što prikazuje, a u frontendu nema `dangerouslySetInnerHTML`. CSV izvoz statistike stavlja apostrof ispred naziva koji počinje sa `=`, `+`, `-` ili `@` (`OccupancyCsvTest.nazivKojiExcelCitaKaoFormuluDobijaApostrof`). Slika resursa se prepoznaje po prvim bajtovima, ne po imenu ni `Content-Type` (`ImageTypeTest.htmlIRiffKojiNijeWebpNisuSlika`).

Nije pokriveno: CSV nije ručno otvoren u Excelu, apostrof je po dokumentaciji. Nema testa koji šalje SQL u polja, oslanjam se na to da upiti nemaju gdje primiti nešto što nije parametar.

## A06 Insecure Design

Pravila koja se ne smiju prekršiti čuva baza, ne samo Java kod: preklapanje termina (`EXCLUDE`), email malim slovima, anonimiziran obrisan nalog (`users_obrisan_anoniman`), mail pozivnice uvijek s pozivnicom. Rezervacija ima `Idempotency-Key`, pa ponovljen zahtjev ne pravi drugu.

Dokaz: `BookingIstovremenoApiTest` (20 niti za isti termin, prolazi tačno jedna), `AvailabilityIstovremenoTest`, `RegistracijaIstovremenoTest`, `InviteIstovremenoTest`, `LoginLockIstovremenoTest`, `AccountDeletionApiTest` (dva admina brišu nalog u isto vrijeme, organizacija nikad ne ostane bez admina).

Nije pokriveno: granica od tri maila na sat se broji bez zaključavanja, pa istovremeni zahtjevi mogu proći preko nje (`docs/ODLUKE.md`). Ako se resurs pravi u istom trenutku kad se briše nalog njegovog providera, jedno od dvoje padne na stranom ključu sa `500`. Nije testirano.

## A07 Authentication Failures

Prijava ima granicu od 20 pokušaja u minuti po IP adresi, a registracija 20 na sat. Pet pogrešnih lozinki za isti email zaključava prijavu na 15 minuta, i za email bez naloga, da se po tome ne vidi ko ima nalog. Registracija uvijek vraća `202`, a prijava radi tek poslije potvrde emaila. Refresh token se rotira, a ponovo poslan stari token poništi cijelu porodicu. Nova lozinka poništava sve sesije i stare linkove.

Dokaz: `AuthRateLimitApiTest` (i da lažni `X-Forwarded-For` ne daje nove pokušaje), `LoginLockApiTest`, `RegisterApiTest.postojeciEmailDobijaIstiOdgovorAVlasnikMail`, `VrijemeOdgovoraTest`, `RefreshApiTest.ponovoPoslanStariTokenPonistavaCijeluPorodicu`, `PasswordResetApiTest`. Frontend pokazuje sat sljedećeg pokušaja iz `Retry-After` (`porukaGreske.test.js`).

Nije pokriveno: reset lozinke za postojeći email je sporiji 6 do 15 ms nego za nepostojeći (mjereno kroz MockMvc), pa se mjerenjem vremena možda može saznati ko ima nalog. Produkcija broji IP iz `CF-Connecting-IP`, po Renderovom vodiču. Kad zahtjev ide preko Vercela, tu stoji Vercelova adresa, a ne posjetiočeva, i 25.11.2026 je 30 prijava kroz Vercel u minuti prošlo bez `429` (`docs/ODLUKE.md`). Granica po IP-u na live adresi zato ne radi, štiti samo zaključavanje po emailu. Nema dvofaktorske prijave.

## A08 Software or Data Integrity Failures

JWT se provjerava potpisom (`NimbusJwtDecoder`, samo HS256). Svaka izmjena rezervacije ide kroz dozvoljene prelaze stanja s `@Version`, a svaki prelaz ostaje u `booking_events`. Osjetljive radnje upisuju zapis u `audit_log` u istoj transakciji (`@Transactional(propagation = MANDATORY)`), pa radnja bez zapisa ne može proći.

Dokaz: `BookingPrelaziApiTest`, `BookingTest`, `AuditLogApiTest`. Na GitHubu se commitovi ne potpisuju, a CI ne pravi artefakte koji idu u produkciju (Render builda sam iz repoa).

## A09 Security Logging and Alerting Failures

`audit_log` bilježi pravljenje organizacije, pozivnice, izmjene resursa i brisanje naloga, s akterom i vremenom. Admin ga čita za svoju organizaciju (`AuditLogApiTest`). Ponovo upotrijebljen refresh token ide u log kao upozorenje. Neočekivana greška ide u log s cijelim izuzetkom, a klijent dobija samo opštu poruku.

Nije pokriveno: od Faze 9 ERROR linije idu u Sentry, s ID-jem zahtjeva. Zaključavanja prijave, `429` i ponovljeni refresh token nisu greške, pa se vide samo u logu Rendera, a njega niko redovno ne gleda. Pogrešne prijave nisu u `audit_log`, samo u `login_failures`, koja se čisti.

## A10 Mishandling of Exceptional Conditions

Sve greške idu kroz `GlobalExceptionHandler` kao Problem Details, s porukom na bosanskom i bez poruke izuzetka. Greška u sredini transakcije vraća sve nazad. Brisanje naloga je jedna transakcija, i bez nje `AuditLogService` odbije zapis. Kad mail ne ode, obavijest ostaje u redu i šalje se ponovo s odgodom.

Dokaz: `GlobalExceptionHandlerTest.neocekivanaGreskaNeOtkrivaPorukuIzuzetka`, `GlobalExceptionHandlerApiTest.neocekivanaGreskaVracaServerErrorBezInternihDetalja`, `NotificationSenderTest`.

Nije pokriveno: obavijest koja baci grešku koja nije `MailException` ostaje prva u redu i blokira ostale u tom krugu posla (dug iz Faze 5).

## CodeQL

Od 22.11.2026 CodeQL (`security-extended`) analizira Javu i JavaScript na svaki push, na pull request i jednom sedmično. Nalazi su u Security > Code scanning na GitHubu. Broj nalaza iz prve analize: _(upisati poslije pregleda u GitHubu)_.

## Na produkciji

Zaglavlja i CORS sam 26.11.2026 provjerio curlom. `/api/status`, i direktno na Renderu i kroz Vercel, vraća CSP `default-src 'none'; frame-ancestors 'none'`, HSTS, `nosniff`, `X-Frame-Options: DENY` i `Referrer-Policy: no-referrer`. Preflight s tuđeg origina dobije `403`, a s Vercel adrese `200`, s tom adresom u `Access-Control-Allow-Origin`.

Granica pokušaja prijave po IP-u kroz Vercel ne radi, jer u `CF-Connecting-IP` vidi Vercelovu adresu (`docs/RUNBOOK.md`).

Frontend na Vercelu nema CSP, samo HSTS, koji Vercel stavlja sam (provjereno 26.11.2026). CSP bi išao u `frontend/vercel.json` kao `headers`, ali ga prvo treba probati sa service workerom, fontovima i povratkom sa Stripea, pa je zapisan u Dodacima. Refresh kolačić (`SameSite=Strict`) radi samo dok su frontend i `/api` na istom sajtu, što je razlog zašto Vercel prosljeđuje `/api` na Render.

Pentesta ni automatskog skeniranja žive aplikacije (npr. OWASP ZAP) nema.
