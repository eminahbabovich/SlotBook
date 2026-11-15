# Dizajn

Izabran je smjer **Red vožnje**, 15.11.2026. Bila su tri prijedloga (druga dva su bila katalog čitaonice i šalterski listić), a uzeo sam onaj koji je preporučio AI mentor, jer je kalendar termina po prirodi red vožnje: vremena u koloni, termini kao redovi, a na telefonu lista. Implementira se u Fazi 7.

Pravilo: boja, font, razmak ili zaobljenje kojeg ovdje nema ne ulazi u UI. Ako nešto zatreba, prvo se dopiše ovdje, pa tek onda u kod. Vrijednosti u kodu su CSS varijable na jednom mjestu.

## Ideja

Inspiracija je tabla s polascima na stanici i tramvajski red vožnje na stajalištu. Dan je tabla, a termin je jedan red: vrijeme lijevo, tabularnim ciframa, poravnato kao polasci. Linije su jake i horizontalne, a sjena i zaobljenih kartica gotovo nema. Žuta je jedina jaka boja i znači samo jedno: ovo je tvoje, kao istaknut polazak. Izuzetaka nema, ni za fokus.

## Boje

Kontrast je računat po WCAG formuli za relativnu luminansu. Svaki par prolazi AA za običan tekst (najmanje 4,5:1).

| Varijabla | Hex | Uloga | Kontrast |
|---|---|---|---|
| `--podloga` | `#F4F1EA` | papir reda vožnje, pozadina svega | |
| `--tekst` | `#16191D` | tekst, linije, ikone | 15,63 na podlozi |
| `--tekst-sporedni` | `#5A6069` | datum ispod naslova, napomene | 5,62 na podlozi |
| `--moje` | `#F2B705` | samo moja rezervacija | tekst na žutoj 9,70 |
| `--greska` | `#B3261E` | greška i ono što se ne može vratiti | 5,79 na podlozi |

Žuta se ne koristi za dugmad, linkove ni ukras. Ako je žuto, korisnik zna da je to njegov termin.

## Fontovi

`Big Shoulders Display` je za naslove: uzak je, kao natpisi na stanici. `IBM Plex Sans` je za sav tekst, a `IBM Plex Mono` za vremena, datume i brojeve, s tabularnim ciframa, da se 08:00 i 11:00 poravnaju. Svi imaju subset `latin-ext` (č, ć, š, đ, ž), što sam provjerio preko Fontsource API-ja. Self-hostaju se preko Fontsource paketa, ne učitavaju se s Google servera.

U browseru je provjereno 15.11.2026, u Edgeu, na 375 i 1280 px: "Čitaonica PMF, đački termin, ž" i sva velika i mala slova č, ć, š, đ, ž crta svaki od tri fonta, a ne rezervni font iz sistema. Probna stranica svako slovo izmjeri preko canvasa dvaput, s rezervnim `monospace` i sa `serif`, pa iste širine znače da slovo dolazi iz našeg fonta. Kad sam Mono privremeno sveo na `latin` subset, a Big Shoulders na nepostojeće ime, oba su pokazala slova koja fale. Kvačice i crtu na đ sam pogledao i okom. Na Firefoxu i Safariju nisam provjerio.

Uski display font se ne stavlja na duge naslove i ne ide ispod 24 px, jer se na telefonu teško čita.

Veličine teksta (izabrane 15.11.2026). Osnova je 16 px, a ispod 14 px se ne ide:

| Varijabla | Veličina | Font | Gdje |
|---|---|---|---|
| `--velicina-sitno` | `14px` | Plex Sans | datum ispod naslova, napomene, oznake "MOJE" i "ČEKA POTVRDU" |
| `--velicina-tekst` | `16px` | Plex Sans | sav ostali tekst, dugmad, polja forme |
| `--velicina-vrijeme` | `18px` | Plex Mono | vrijeme u redu termina, da se po njemu traži |
| `--velicina-naslov-3` | `24px` | Big Shoulders | naslov dana na tabli |
| `--velicina-naslov-2` | `32px` | Big Shoulders | naslov dijela ekrana |
| `--velicina-naslov-1` | `40px` | Big Shoulders | naslov ekrana, jedan po ekranu |

Prored je 1,5 za tekst (`--prored-tekst`) i 1,1 za naslove (`--prored-naslov`), jer uski naslov s velikim proredom izgleda kao dva odvojena reda.

## Razmaci i oblik

Razmaci su na mreži od 4 px (4, 8, 12, 16, 24, 32, 48). Red termina je visok najmanje 48 px, da se lako pogodi prstom. Zaobljenje je 2 px ili nikakvo. Sjena se ne koristi, a granice prave linije od 1 px (`--tekst` na 20 % ili puna kod naslova dana).

## U kodu

Sve vrijednosti su u `frontend/src/tokeni.css`, kao CSS varijable. Boje se zovu kao u tabeli iznad. Fontovi su `--font-naslov`, `--font-tekst` i `--font-brojevi`, razmaci `--razmak-4` do `--razmak-48` (broj je vrijednost u px), a tu su još `--zaobljenje` (2 px), `--linija` (`--tekst` na 20 %) i `--red-min-visina` (48 px). Test `tokeni.test.js` pada kad se boje u `tokeni.css` razlikuju od tabele u ovom fajlu i kad neki drugi fajl u `frontend/src` napiše boju direktno (hex, `rgb()`, `hsl()`).

Isti test provjerava i veličine teksta: tabela veličina ovdje i `tokeni.css` moraju se slagati, a `font-size` i `fontSize` s brojem van `tokeni.css` obaraju test.

## Forme i dugmad

Polje ima punu tamnu ivicu od 1 px, jer ivica polja treba kontrast 3:1, a `--linija` ga nema. Neispravno polje dobije ivicu od 3 px u boji `--greska` i tekst greške ispod, pa se razlikuje i bez boje. Oznaka je iznad polja, nikad samo placeholder. Glavno dugme je tamno (`--tekst`) sa svijetlim tekstom (`--podloga`), a dok čeka odgovor sivo (`--tekst-sporedni`) i kaže šta radi ("Prijava u toku"). Prelaz na drugi ekran (npr. "Napravi nalog" ispod prijave) je podvučen tekst, ne dugme. Greška cijele forme je tekst s debelom crvenom linijom lijevo, bez pozadine i ikone.

Lista organizacija je kao tabla s polascima: red po organizaciji, naziv lijevo, uloga desno sitnijim sivim slovima, tanka linija (`--linija`) između redova i puna tamna linija iznad prvog. Red je visok najmanje `--red-min-visina`, cijeli je link i na hover podvlači naziv. Nema kartica ni sjene.

Kalendar je jedan dan kao lista, i na telefonu i na desktopu. Iznad je traka sa sedam dana (kratki naziv i broj u dva reda), a izabran dan ima tamnu ivicu od 3 px i podebljan tekst. Termin je red visine `--red-min-visina`: vrijeme lijevo u `--font-brojevi` veličine `--velicina-vrijeme`, oznaka stanja desno. Šrafura zauzetog termina je `--linija` pod 135 stepeni, 4 px pruga na svakih 12 px (`--razmak-4`, `--razmak-12`). Moja na čekanju ima isprekidanu žutu ivicu od 3 px. Žuta ivica na podlozi ima kontrast samo 1,61:1, pa stanje ne nosi ivica nego tekst "ČEKA POTVRDU". Isključeno tekst-dugme (npr. "Prethodnih 7 dana" na prvoj sedmici) je sivo i bez podvlake.

Potvrda rezervacije nije modal, nego red odmah ispod izabranog termina, s tamnom linijom lijevo (`--razmak-4`, `--tekst`), isto kao greška forme, samo tamna. Pitanje je podebljano ("Rezervisati ponedjeljak, 19. novembar, 08:00 do 09:00?"), ispod su glavno dugme i "Odustani" kao tekst. Na telefonu se tako vidi bez skrolanja, a pored termina na koji se odnosi. Raspored providera koristi istu listu, samo umjesto resursa i organizacije piše ime klijenta, a ispod su dugmad za promjenu. Glavna akcija (Potvrdi, Upiši dolazak) je puno dugme, a ona koja se ne može vratiti (Odbij, Upiši nedolazak) je tekst-dugme i traži drugi klik.

Moje rezervacije su ista vrsta liste: dan običnim fontom, sati u `--font-brojevi`, ispod resurs i organizacija, pa stanje sitnim sivim slovima. Potvrđena ima punu žutu liniju lijevo (`--razmak-4`), a ona na čekanju isprekidanu, isto kao "MOJE" i "ČEKA POTVRDU" u kalendaru. Otkazana, odbijena i istekla imaju precrtano vrijeme. Stanje uvijek piše i riječima.

Poruka poslije uspjeha je podebljan tekst ispod liste, bez ikone i zelene boje, jer zelene u paleti nema.

Forma je široka najviše `--sirina-forme` (400 px), da red za email na desktopu ne bude preko cijelog ekrana. Tu vrijednost sam potvrdio 15.11.2026, poslije koraka 4 Faze 7.

## Fokus

Fokus je tamni okvir od 3 px u boji `--tekst`, odmaknut 2 px od elementa (`--fokus`, `--fokus-odmak`). Prvo je bio planiran žuti, ali žuta na podlozi ima kontrast 1,61:1, a WCAG traži najmanje 3:1 za okvir fokusa. Tamni ima 15,63:1 na podlozi i 9,70:1 na žutoj traci moje rezervacije, pa se vidi i tamo. Ostale kontraste iz tabele boja sam izračunao po istoj formuli i slažu se.

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

Pristupačnost je dio poštovanja, ne dodatak: kontrast AA, sve radi tastaturom, fokus je vidljiv (tamni okvir), a čitač ekrana čita cijelu rečenicu ("utorak, 19. novembar, 08:00 do 09:00, slobodno").
