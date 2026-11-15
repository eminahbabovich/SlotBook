# Dizajn

Izabran je smjer **Red vožnje**, 15.11.2026. Bila su tri prijedloga (druga dva su bila katalog čitaonice i šalterski listić), a uzeo sam onaj koji je preporučio AI mentor, jer je kalendar termina po prirodi red vožnje: vremena u koloni, termini kao redovi, a na telefonu lista. Implementira se u Fazi 7.

Pravilo: boja, font, razmak ili zaobljenje kojeg ovdje nema ne ulazi u UI. Ako nešto zatreba, prvo se dopiše ovdje, pa tek onda u kod. Vrijednosti u kodu su CSS varijable na jednom mjestu.

## Ideja

Inspiracija je tabla s polascima na stanici i tramvajski red vožnje na stajalištu. Dan je tabla, a termin je jedan red: vrijeme lijevo, tabularnim ciframa, poravnato kao polasci. Linije su jake i horizontalne, a sjena i zaobljenih kartica gotovo nema. Žuta je jedina jaka boja i znači samo jedno: ovo je tvoje, kao istaknut polazak. Jedini izuzetak je okvir fokusa za tastaturu.

## Boje

Kontrast je računat po WCAG formuli za relativnu luminansu. Svaki par prolazi AA za običan tekst (najmanje 4,5:1).

| Varijabla | Hex | Uloga | Kontrast |
|---|---|---|---|
| `--podloga` | `#F4F1EA` | papir reda vožnje, pozadina svega | |
| `--tekst` | `#16191D` | tekst, linije, ikone | 15,63 na podlozi |
| `--tekst-sporedni` | `#5A6069` | datum ispod naslova, napomene | 5,62 na podlozi |
| `--moje` | `#F2B705` | samo moja rezervacija i fokus | tekst na žutoj 9,70 |
| `--greska` | `#B3261E` | greška i ono što se ne može vratiti | 5,79 na podlozi |

Žuta se ne koristi za dugmad, linkove ni ukras. Ako je žuto, korisnik zna da je to njegov termin.

## Fontovi

`Big Shoulders Display` je za naslove: uzak je, kao natpisi na stanici. `IBM Plex Sans` je za sav tekst, a `IBM Plex Mono` za vremena, datume i brojeve, s tabularnim ciframa, da se 08:00 i 11:00 poravnaju. Svi imaju subset `latin-ext` (č, ć, š, đ, ž), što sam provjerio preko Fontsource API-ja. Self-hostaju se preko Fontsource paketa, ne učitavaju se s Google servera.

U browseru je provjereno 15.11.2026, u Edgeu, na 375 i 1280 px: "Čitaonica PMF, đački termin, ž" i sva velika i mala slova č, ć, š, đ, ž crta svaki od tri fonta, a ne rezervni font iz sistema. Probna stranica svako slovo izmjeri preko canvasa dvaput, s rezervnim `monospace` i sa `serif`, pa iste širine znače da slovo dolazi iz našeg fonta. Kad sam Mono privremeno sveo na `latin` subset, a Big Shoulders na nepostojeće ime, oba su pokazala slova koja fale. Kvačice i crtu na đ sam pogledao i okom. Na Firefoxu i Safariju nisam provjerio.

Uski display font se ne stavlja na duge naslove i ne ide ispod 24 px, jer se na telefonu teško čita.

## Razmaci i oblik

Razmaci su na mreži od 4 px (4, 8, 12, 16, 24, 32, 48). Red termina je visok najmanje 48 px, da se lako pogodi prstom. Zaobljenje je 2 px ili nikakvo. Sjena se ne koristi, a granice prave linije od 1 px (`--tekst` na 20 % ili puna kod naslova dana).

## Stanja termina

Svako stanje se razlikuje i bez boje: po ivici, šrafuri, tekstu i oznaci.

- **slobodno:** red s tankom ivicom, vrijeme i riječ "slobodno";
- **zauzeto:** siva kosa šrafura preko reda, precrtano vrijeme i riječ "zauzeto";
- **moja rezervacija:** puna žuta traka, tamni tekst i oznaka "MOJE";
- **moja na čekanju:** žuta isprekidana ivica bez punjenja i oznaka "ČEKA POTVRDU".

Vrijeme je uvijek u zoni organizacije. Kad se zona browsera razlikuje, uz vrijeme piše "po sarajevskom vremenu". Ne piše "GMT+1".

## Da ne liči na sve ostalo

Najlakše je napraviti ekran koji izgleda kao hiljadu drugih: bijela kartica sa sjenom, ljubičasto dugme, ikonica uz svaki naslov i rečenica "Dobrodošli nazad!". To ne radimo. Prije nego što ekran bude gotov, pitam se dvije stvari. Da li se prepozna kao SlotBook kad se sakrije logo? Da li bi isti ekran mogao stajati u bilo kojoj drugoj aplikaciji? Ako bi mogao, nije gotov.

Svaki element mora nešto značiti. Linija dijeli dane, žuta znači "moje", šrafura znači "zauzeto". Ako ne znam reći šta neka boja, linija ili ikona govori korisniku, ona ne ide na ekran.

Ne koristimo:
- gradijente, sjaj, staklo (glassmorphism) i mutnu pozadinu;
- ilustracije s ljudima i blobovima, 3D ikone i slike laptopa;
- ikonicu uz svaki naslov i iskre koje znače "pametno";
- jednako zaobljene kartice sa sjenom za svaki sadržaj;
- dugmad u obliku pilule posvuda;
- konfete ili animaciju "uspjeh" poslije rezervacije;
- emoji u interfejsu.

Ikone su iz jednog linijskog seta i stoje samo tamo gdje zamjenjuju riječ ili joj pomažu: katanac uz zauzeto, strelica za sljedeći dan. Animacija postoji samo kad objašnjava promjenu, npr. kad termin koji je neko drugi upravo uzeo pređe u zauzeto, i poštuje `prefers-reduced-motion`.

Raspored dolazi iz sadržaja, ne iz mreže kartica. Vrijeme je lijevo i šire, jer se po njemu traži. Tabla može biti gusta kao pravi red vožnje: ljudi znaju čitati red vožnje i ne treba im prazan prostor oko svakog termina.

Lokalni detalji su dio identiteta: dani i mjeseci malim slovom na bosanskom (utorak, 19. novembar), 24-satno vrijeme, datum kao 19. 11., primjeri i demo podaci iz čitaonice, konsultacija i teretane.

## Kako tekst zvuči

Aplikacija govori kao osoba na šalteru koja zna posao: kratko, tačno, s vremenom i brojem. Ne govori kao marketing, a ni kao robot koji se izvinjava.

Pravila:
- **Prvo šta se desilo, pa šta sada.** Uvijek konkretno: koji termin, koje vrijeme, koji dan.
- **Obraća se sa "ti"**, kao poruke grešaka na backendu.
- **Rodno neutralno:** bez "rezervisao/la" i "dobrodošao/la". Piše se "Rezervacija je napravljena", "Imaš termin u 08:00".
- **Bez uzvičnika i bez emojija.**
- **Dugme kaže šta radi:** glagol i predmet, npr. "Rezerviši 08:00" ili "Otkaži rezervaciju", a ne "Nastavi", "Potvrdi" ili "OK".
- **Prazno stanje kaže šta dalje:** "U utorak nema slobodnih termina. U srijedu ih ima četiri.", a ne "Nema podataka".
- **Učitavanje kaže šta se učitava:** "Učitavaju se termini za utorak", a ne "Učitavanje...".
- **Ne hvali se, ne zahvaljuje, ne objašnjava očito.**

Zabranjeno, jer odmah zvuči kao generisan tekst:
- "Dobrodošli nazad", "Spremni ste", "Sve je postavljeno";
- "Ups! Nešto je pošlo po zlu" i "Nažalost, došlo je do greške";
- "Uspješno!" bez toga šta je uspjelo;
- "besprijekorno", "jednostavno", "na dohvat ruke", "otključajte", "vaše putovanje", "Hajde da...";
- "Molimo pokušajte ponovo kasnije" bez razloga i bez toga kad;
- tri pridjeva zaredom i rečenice koje se završavaju pitanjem korisniku.

Prije i poslije:

| Ne | Da |
|---|---|
| Ups! Nešto je pošlo po zlu. | Termin u 08:00 je u međuvremenu zauzet. Najbliži slobodan je u 09:00. |
| Uspješno ste rezervisali termin! | Sto br. 3 je tvoj u utorak, 19. 11., od 08:00 do 09:00. Potvrda stiže mailom. |
| Nema podataka za prikaz. | Još nemaš rezervacija. Slobodni termini su u kalendaru čitaonice. |
| Da li ste sigurni? | Otkazati rezervaciju za utorak u 08:00? Termin će odmah biti slobodan za druge. |
| Greška pri učitavanju. | Termini se nisu učitali, server ne odgovara. Pokušaj ponovo za minutu. |

## Za koga je

Za studenticu koja pred ispit traži sto u čitaonici, za profesora koji drži konsultacije i za osobu koja vodi salu u teretani. Niko od njih ne želi provoditi vrijeme u aplikaciji. Žele termin i da znaju da je njihov. Zato aplikacija ne požuruje i ne vara. Otkazivanje se ne skriva, nema odbrojavanja ("još samo 2 termina!") koje nije stvarno, i nema dugmeta za odbijanje koje posramljuje ("Ne, ne treba mi termin").

Pristupačnost je dio poštovanja, ne dodatak: kontrast AA, sve radi tastaturom, fokus je vidljiv (žuti okvir), a čitač ekrana čita cijelu rečenicu ("utorak, 19. novembar, 08:00 do 09:00, slobodno").
