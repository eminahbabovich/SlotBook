# Dizajn

Smjer je **Čelik i žar**, izabran 25.11.2026 za redizajn u Fazi 12. Zamijenio je smjer **Red vožnje** iz Faze 7 (15.11.2026), koji je bio čitljiv, ali bez lica: krem papir, crni tekst i linkovi umjesto dugmadi. Prijedlog s razlozima, skicama i samokritikom je u [redizajn/smjer.md](redizajn/smjer.md), a stanje prije u [redizajn/inventar-prije.md](redizajn/inventar-prije.md).

Redizajn ide korak po korak (`REDESIGN.md`, sekcija 9). Boje, fontovi i veličine ispod već važe. Sekcije od "Razmaci i oblik" naniže još opisuju komponente iz Faze 7 i prepisuju se kad dođe red na svaku. Gdje tamo piše "žuta", sad je žar.

Pravilo: boja, font, razmak ili zaobljenje kojeg ovdje nema ne ulazi u UI. Ako nešto zatreba, prvo se dopiše ovdje, pa tek onda u kod. Vrijednosti u kodu su CSS varijable na jednom mjestu.

## Ideja

Zenica je grad čelika i željeznički čvor: sivi lim, utisnuta slova na natpisnim pločicama mašina i vagona, kartonska karta za voz koju kondukter probuši. Podloga je hladan čelik, naslovi su široka teška slova, a rezervacija je karta. Jedina topla boja je žar i znači samo jedno: ovo je tvoje.

## Boje

Paleta je s Color Hunta ([37353e 44444e 715a5a d3dad9](https://colorhunt.co/palette/37353e44444e715a5ad3dad9) i žar iz [524646 a8a492 fcf2e5 ec5b38](https://colorhunt.co/palette/524646a8a492fcf2e5ec5b38)). Površina i tekst na žaru su izvedeni iz nje, a sporedni tekst je zatamnjen da prođe AA. Kontrast je po WCAG formuli za relativnu luminansu.

| Varijabla | Hex | Uloga | Kontrast |
|---|---|---|---|
| `--podloga` | `#D3DAD9` | čelik, pozadina svega | |
| `--povrsina` | `#E9EDEC` | lim: polja, red termina, karta | |
| `--tekst` | `#37353E` | grafit: tekst, linije, glavno dugme | 8,50 na podlozi, 10,22 na površini |
| `--tekst-na-moje` | `#1F1E24` | ugalj: tekst na žaru | 4,82 na žaru |
| `--tekst-sporedni` | `#5E4A4A` | oksid: napomene, datum ispod naslova | 5,79 na podlozi, 6,96 na površini |
| `--moje` | `#EC5B38` | žar: samo moja rezervacija | 2,42 na podlozi, zato nikad tekst |
| `--greska` | `#9A2A22` | greška, uvijek uz ikonu i tekst | 5,42 na podlozi |

Žar se ne koristi za dugmad, linkove ni ukras. Glavno dugme je grafit. Greška i žar su bliske boje, pa se greška nikad ne prepoznaje samo po boji.

## Fontovi

**Archivo** (`@fontsource-variable/archivo`, osa širine 62 do 125) je za sve osim karte. Naslovi su široki i teški (`--sirina-naslov` 125 %, težina 800), tekst normalne širine. Vremena su isti font s `tabular-nums`, pa monospace više ne treba. **IBM Plex Mono** je samo na karti (`--font-karta`), kao otisak štampača na blagajni. Big Shoulders i Plex Sans su izbačeni.

Provjereno 25.11.2026 u Edgeu: svih deset slova č, ć, š, đ, ž crta Archivo, a ne rezervni font (canvas, isto mjerenje kao u Fazi 7). Uz `tabular-nums` su "11:11" i "00:00" iste širine (104 px), a bez njega nisu (96 i 104). Plex Mono je provjeren 15.11.2026.

Veličine teksta (skala 1,25). Osnova je 16 px, a ispod 14 px se ne ide:

| Varijabla | Veličina | Gdje |
|---|---|---|
| `--velicina-sitno` | `14px` | napomene, oznake stanja |
| `--velicina-tekst` | `16px` | sav ostali tekst, dugmad, polja forme |
| `--velicina-vrijeme` | `20px` | vrijeme u redu termina, težina 600 |
| `--velicina-naslov-3` | `25px` | naslov dana, podnaslov |
| `--velicina-naslov-2` | `31px` | naslov dijela ekrana |
| `--velicina-naslov-1` | `39px` | naslov ekrana, jedan po ekranu |

Prored je 1,5 za tekst (`--prored-tekst`) i 1,05 za naslove (`--prored-naslov`), jer široka teška slova s velikim proredom izgledaju kao dva odvojena reda.

## Razmaci i oblik

Razmaci su na mreži od 4 px (4, 8, 12, 16, 24, 32, 48). Red termina je visok najmanje 48 px, da se lako pogodi prstom. Zaobljenje ima hijerarhiju: redovi i trake 0, polja i dugmad 3 px (`--zaobljenje`), karta 12 px (`--zaobljenje-karta`). Sjena se ne koristi, a granice prave linije od 1 px (`--tekst` na 20 % ili puna kod naslova dana).

## U kodu

Sve vrijednosti su u `frontend/src/tokeni.css`, kao CSS varijable. Boje se zovu kao u tabeli iznad. Fontovi su `--font-naslov`, `--font-tekst` i `--font-brojevi`, razmaci `--razmak-4` do `--razmak-48` (broj je vrijednost u px), a tu su još `--zaobljenje` (3 px), `--zaobljenje-karta` (12 px), `--linija` (`--tekst` na 20 %) i `--red-min-visina` (48 px). Test `tokeni.test.js` pada kad se boje u `tokeni.css` razlikuju od tabele u ovom fajlu i kad neki drugi fajl u `frontend/src` napiše boju direktno (hex, `rgb()`, `hsl()`).

Isti test provjerava i veličine teksta: tabela veličina ovdje i `tokeni.css` moraju se slagati, a `font-size` i `fontSize` s brojem van `tokeni.css` obaraju test.

## Forme i dugmad

Polje je lim (`--povrsina`) s punom grafitnom ivicom od 1 px, jer ivica polja treba kontrast 3:1, a `--linija` ga nema. Neispravno polje dobije ivicu od 3 px u boji `--greska` i tekst greške ispod, pa se razlikuje i bez boje. Oznaka je iznad polja, nikad samo placeholder. Zaobljenje polja i dugmeta je `--zaobljenje` (3 px), a redovi i trake su ravni.

Glavno dugme je grafitna ploča (`--tekst`) sa svijetlim tekstom (`--povrsina`, 10,22:1) i donjom ivicom od uglja debljine `--ivica-dugmeta` (3 px), pa izgleda utisnuto. Na hover potamni u ugalj, a na klik se spusti za 1 px za `--trajanje-brzo` (150 ms), dok se donja ivica stanji, pa visina ostaje ista. Dok čeka odgovor, dugme je oksid (`--tekst-sporedni`) i kaže šta radi ("Prijava u toku"). Prelaz na drugi ekran (npr. "Napravi nalog" ispod prijave) je podvučen tekst, ne dugme.

Greška cijele forme i greška polja imaju ikonu prekinutog kruga s uzvičnikom ispred teksta, jer su `--greska` i žar bliske boje i greška se ne smije prepoznavati samo po boji. Ikona je CSS maska u boji teksta, pa u SVG-u nema upisane boje. Greška forme ima još debelu crvenu liniju lijevo i crveni tekst (5,42:1), bez pozadine.

Trajanja su `--trajanje-brzo` (150 ms, odgovor na klik), `--trajanje-srednje` (220 ms, promjena stanja) i `--trajanje-karta` (300 ms). Uz `prefers-reduced-motion` su svi prelazi i animacije 0 ms: stanje se i dalje promijeni, samo bez pokreta.

Liste (organizacije, resursi, članovi, izuzeci) su ploče lima, iste kao red termina: svaki red je `--povrsina`, a između redova je procjep od 2 px kroz koji se vidi podloga. Nema kartica, sjene ni linije iznad liste. Naziv je `--velicina-vrijeme` (20 px) i težak 700, jer se po njemu traži, a uloga ili trajanje su sitno desno u `--tekst-sporedni`. Red je visok najmanje `--red-min-visina`, cijela ploča je link i na hover podvlači naziv. Kod resursa koji korisnik vodi, "Raspored" i "Uredi" su podvučene veze na istoj ploči, desno.

Kalendar je jedan dan kao lista, i na telefonu i na desktopu. Iznad je traka sa sedam dana kao lenjir: dani su odvojeni tankim podiocima (`--linija`), ispod cijele trake je grafitna linija, a izabran dan ima debelu crtu ispod (`--razmak-4`) i podebljan tekst. Broj dana je u `--velicina-vrijeme`, težine 600. Dan u kojem imam termin (potvrđen ili na čekanju) ima tačkicu od žara od 8 px s prstenom od uglja, a čitač ekrana čuje "imaš termin". Ispod naslova resursa je jedan red s onim što se pita prije izbora: trajanje, cijena ili "besplatno", i broj mjesta kad ih je više od jednog. Termin je limena ploča visine `--red-min-visina`, s procjepom od 2 px do sljedeće: vrijeme lijevo u `--velicina-vrijeme`, težine 600 i s tabularnim ciframa, a desno oznaka stanja, cijena (samo kod plaćenog resursa i samo na terminu koji se može rezervisati, npr. "15 KM") i krug ili ikona. Šrafura zauzetog termina je `--linija` pod 135 stepeni, 4 px pruga na svakih 12 px, preko podloge, bez ploče, pa zauzet termin izgleda kao udubljenje. Kostur dok se termini učitavaju su četiri prazne ploče koje polako "dišu" (prozirnost od 1 do 0,5 za `--trajanje-disanje`, 1200 ms), a uz `prefers-reduced-motion` stoje mirno. Isključeno tekst-dugme (npr. "Prethodnih 7 dana" na prvoj sedmici) je sivo i bez podvlake.

Desno od liste (na desktopu od 900 px) je karta, potpisni element iz `docs/redizajn/smjer.md`. Lista i karta su u mreži `1fr` i 320 do 400 px, a karta je ljepljiva uz vrh dok se lista skrola. Dok termin nije izabran, na njenom mjestu je isprekidan obris (`--linija`, 2 px) s rečenicom šta će se tu pojaviti. Izabran termin otvara kartu u nastajanju: dan, put od početka do kraja, resurs, ponavljanje i dugme "Rezerviši", koje dok čeka server kaže "Rezervišem..." i zaključa se. Na telefonu je karta ispod liste, a dok je termin izabran prikovana je za dno ekrana (`position: sticky`), s grafitnom linijom iznad i podlogom ispod, pa ne gura listu. Tamo nema zaglavlja ni naziva resursa, jer je naziv već naslov stranice.

Karta je lim (`--povrsina`) s grafitnom ivicom od 1 px i zaobljenjem `--zaobljenje-karta`, bez sjene. Zaglavlje je u `--font-karta` (IBM Plex Mono): "SlotBook" i "Karta № 1DDE-8D24", gdje je broj prvih osam znakova ID-ja rezervacije u dva dijela. Put od-do su dva vremena u `--font-karta` veličine `--velicina-naslov-3` i pruga od 2 px s dvije stanice od 10 px. Desno je kupon od 112 px, odvojen isprekidanom perforacijom, sa zarezima gore i dolje (krugovi u boji podloge). Na kuponu je žig: stanje riječima u dvostrukom okviru od 3 px, nakrivljen 8 stepeni ("potvrđena", "čeka potvrdu", "čeka uplatu"). Kad server potvrdi rezervaciju, karta izađe iz automata: otkrije se odozgo prema dole i klizne 16 px na mjesto za `--trajanje-karta` (300 ms). To je jedini orkestriran pokret u aplikaciji. Uz `prefers-reduced-motion` karta se samo pojavi. Karta je HTML, ne slika, pa je čita i čitač ekrana ("Karta 1DDE-8D24").

Potvrda rezervacije nije modal, nego red odmah ispod izabranog termina, s tamnom linijom lijevo (`--razmak-4`, `--tekst`), isto kao greška forme, samo tamna. Pitanje je podebljano ("Rezervisati ponedjeljak, 19. novembar, 08:00 do 09:00?"), ispod su glavno dugme i "Odustani" kao tekst. Na telefonu se tako vidi bez skrolanja, a pored termina na koji se odnosi. Raspored providera koristi istu listu, samo umjesto resursa i organizacije piše ime klijenta, a ispod su dugmad za promjenu. Glavna akcija (Potvrdi, Upiši dolazak) je puno dugme, a ona koja se ne može vratiti (Odbij, Upiši nedolazak) je tekst-dugme i traži drugi klik.

Moje rezervacije su karte (iste kao u kalendaru), jedna ispod druge s razmakom `--razmak-16`, najviše 720 px široke. Na karti su broj, dan, put od-do, resurs, organizacija, pa napomene (rok za otkazivanje, rok za uplatu), "Otkaži", "Plati" i "QR kod za dolazak". Stanje je žig na kuponu, riječima, a uz njega i oblik: potvrđena ima žig od žara (ugalj na žaru, 4,82:1), na čekanju cijela karta ima isprekidanu ivicu, otkazana, odbijena i istekla imaju sivi tekst i precrtana vremena, a završena ima rupu kroz kupon (krug u boji podloge s tamnim rubom), kao od kondukterskih kliješta. Prvo je cijeli kupon potvrđene bio od žara, ali pet takvih karata jedna ispod druge je izgledalo kao niz narandžastih traka, pa je žar ostao samo na žigu. Animacija utiskivanja žiga "otkazana" iz skice nije urađena: otkazana karta se poslije osvježavanja liste ponovo napravi, pa bi se žig utiskivao i pri svakom otvaranju stranice. Raspored (za onoga ko vodi resurs) i lista čekanja su redovi lima kao termini u kalendaru, s trakom od žara lijevo za potvrđenu (puna) i onu na čekanju (isprekidana).

Poruka poslije uspjeha je podebljan tekst ispod liste, bez ikone i zelene boje, jer zelene u paleti nema.

Link za kalendar (Moj nalog) je obično polje samo za čitanje: cijeli link se označi kad polje dobije fokus, pa se može kopirati i kad dugme "Kopiraj link" ne radi. Tamno dugme je samo jedno: "Napravi link" dok linka nema, a "Kopiraj link" odmah poslije. "Napravi novi link" i "Ugasi link" su sporedne radnje, podvučen tekst. Dugme i sporedne radnje stoje u jednom redu s razmakom `--razmak-16`, a na uskom ekranu se prelome jedno ispod drugog. Nove boje, fontove ni razmake ovo ne uvodi.

Grupni termin (više mjesta) ima isti red kao običan, a oznaka kaže koliko je mjesta ostalo: "slobodno, još 3 mjesta" ili "još 1 mjesto". Pun grupni termin piše "popunjeno" i ima istu šrafuru kao zauzet, jer "zauzeto" zvuči kao da ga je uzela jedna osoba. Broj mjesta je samo tekst, bez nove boje ili ikone.

QR kod za dolazak se otvara ispod potvrđene rezervacije, na klik na podvučen tekst "QR kod za dolazak", isto kao otkazivanje. Moduli su `--tekst` na `--podloga`, s praznim okvirom od 4 modula, a kod je širok najviše `--sirina-forme`, pa na telefonu zauzme skoro cijelu širinu i kamera ga lako uhvati. Ispod je napomena sitnim sivim slovima. Stranica koju provider otvori iz koda ima samo objašnjenje i jedno tamno dugme "Upiši dolazak". Nove boje, fontove ni razmake ovo ne uvodi.

Forma je široka najviše `--sirina-forme` (400 px), da red za email na desktopu ne bude preko cijelog ekrana. Tu vrijednost sam potvrdio 15.11.2026, poslije koraka 4 Faze 7.

## Prijava kao početni ekran

Odjavljen korisnik na `/` dolazi na prijavu, pa je ona prvi ekran koji neko vidi. Nova ruta nije dodana. Na desktopu (od 900 px) je lijevo jedna rečenica šta je SlotBook, u fontu naslova i veličini `--velicina-naslov-1`, a ispod nje primjer karte za sutra od 10 do 11 (zato da ne izgleda kao stara zaboravljena rezervacija). Desno je forma. Na telefonu je prvo rečenica (`--velicina-naslov-3`), pa forma, a karta je ispod forme, da se prijava vidi bez skrolanja. To je odstupanje od skice u `docs/redizajn/smjer.md`, gdje je karta bila iznad forme. Registracija ima isti uvod. Na užem od 480 px kupon karte je 88 px, a vremena na karti `--velicina-vrijeme`, da se pruga između njih vidi.

## Učitavanje, greška, prazno

Učitavanje je na svim ekranima isto: tri prazne ploče lima koje polako "dišu" (`Ucitava`), u obliku liste koja će stići, a čitač ekrana čuje šta se učitava ("Učitavaju se tvoje rezervacije"). Kalendar ima svoj kostur od četiri ploče, jer zna kakvi su redovi. Ranije je na 20 mjesta stajao samo tekst "Učitava se...".

Greška učitavanja (`NijeUcitano`) je ploča lima s trakom u boji `--greska` lijevo, ikonom i rečenicom šta se nije učitalo, i dugmetom "Pokušaj ponovo". Traka je samo na ploči, ne i na poruci unutra.

Prazno stanje je isprekidan obris (`--linija`, 2 px) sa zaobljenjem karte, isti kao prazna karta u kalendaru, s rečenicom šta se može dalje i, gdje ima smisla, linkom ili dugmetom. Tako izgleda i stranica koja ne postoji.

Radnja koja se ne može vratiti ("Obriši nalog", "Otkaži rezervaciju") je ista ploča kao dugme, ali u boji `--greska` (lim na grešci 6,52:1), da se ne zamijeni s glavnom radnjom. Dok se šalje, siva je kao svako dugme.

Trake u statistici "po stanju" nose značenje kao kalendar: potvrđena i završena su žar s ivicom od uglja, ono što se nije desilo (otkazana, odbijena, istekla, nedolazak) je šrafura, a na čekanju grafit. Broj uvijek piše u redu, pa traka nije jedini nosilac podatka.

Ikona u tabu (`public/favicon.svg`) je mala karta od grafita s kuponom od žara i perforacijom. SVG ne može čitati CSS varijable, pa su tu hex vrijednosti iz tabele boja, a `tokeni.test.js` gleda samo `src/`.

Nije urađeno: polja za vrijeme (`input type=time`) i dalje pokazuju AM/PM kad je browser na engleskom. Format bira browser po svom jeziku, ne po `lang="bs"`, a zamjena drugom kontrolom bi bila promjena ponašanja, ne dizajna.

## Fokus

Fokus je okvir od 3 px u boji `--tekst` (grafit), odmaknut 2 px od elementa (`--fokus`, `--fokus-odmak`). WCAG traži najmanje 3:1 za okvir fokusa. Grafit ima 8,50:1 na podlozi, 10,22:1 na površini i 3,51:1 na žaru, pa se vidi i na mojoj rezervaciji, iako tamo s najmanjom rezervom. Žar kao fokus ne ide: 2,42:1 na podlozi.

## Stanja termina

Svako stanje se razlikuje i bez boje: po ivici, šrafuri, tekstu i oznaci.

- **slobodno:** limena ploča, prazan krug od 14 px s ivicom od 2 px na kraju reda i riječ "slobodno";
- **izabran:** ploča se izdigne 2 px (`--trajanje-brzo`), dobije grafitnu ivicu lijevo (`--razmak-4`) i tamnu liniju ispod, a krug postane pun. Ispod se otvori potvrda;
- **zauzeto:** šrafura preko podloge, bez ploče, precrtano vrijeme i riječ "zauzeto";
- **tvoje:** puna ploča od žara s ivicom od uglja (2 px) i ikonom karte umjesto kruga, tekst u `--tekst-na-moje` i riječ "tvoje";
- **tvoje na čekanju:** limena ploča s isprekidanom ivicom od žara (3 px) i riječ "čeka potvrdu". Isprekidan žar na limu ima samo 2,91:1, pa stanje nosi tekst;
- **ponuđen tebi s liste čekanja** (Faza 10): limena ploča s punom trakom od žara lijevo (`--razmak-4`) i oznaka "ponuđen tebi danas do 17:30" (ili "sutra", kad je ponuda stigla pred ponoć). Termin je tvoj samo do roka, pa nije cijela ploča od žara kao potvrđena rezervacija;
- **tvoje, čeka uplatu** (Faza 11): isto kao na čekanju (isprekidana ivica od žara), samo oznaka kaže "čeka uplatu". Plaćenu ne potvrđuje osoba nego uplata, pa riječ mora reći ko je na potezu;
- **zauzeto, a na listi sam:** isto kao zauzeto (šrafura, precrtano vrijeme), uz tekst "zauzeto, na listi si". Razlika je samo u riječima, jer termin i dalje nije tvoj.

Oznake stanja su malim slovima, a kod tvojih termina podebljane. Ranije su bile verzalom ("MOJE"), ali verzal s razmakom je jedan od znakova AI dizajna iz `REDESIGN.md`.

Zauzet termin se može otvoriti (Faza 10). Ispod njega se otvori isti red kao potvrda rezervacije, s pitanjem i dugmetom "Javi mi ako se oslobodi", a kad sam na listi, s dugmetom "Ispiši me sa liste". Strelice i dalje idu samo kroz termine koji se rezervišu (slobodan i ponuđen meni), a do zauzetog se dolazi Tabom. U "Mojim rezervacijama" je dio "Lista čekanja" iste vrste liste. Ponuđen termin tamo ima žutu traku i link "Rezerviši u kalendaru", a ispis traži drugi klik, jer se mjesto na listi gubi. Vrijeme termina se nikad ne lomi u dva reda (`white-space: nowrap`), a duga oznaka ide u dva reda, poravnata desno. Nove boje, fontove ni razmake ovo ne uvodi.

Vrijeme je uvijek u zoni organizacije. Kad se zona browsera razlikuje, uz vrijeme piše "po sarajevskom vremenu". Ne piše "GMT+1".

Kad se termini promijene preko SSE (neko je rezervisao ili otkazao), kalendar se osvježi bez animacije i bez bljeska: termin samo dobije novo stanje. Čitač ekrana dobije skrivenu najavu "Termini su osvježeni." (`aria-live="polite"`), da promjena ne prođe neprimijećeno. Fokus ostaje gdje je bio. Ako je otvorena potvrda za termin koji je neko upravo uzeo, ona izgleda isto kao poslije `409`: pitanje postane "Termin ... je zauzet.", ispod je greška s crvenom linijom i dugme za najbliži slobodan termin.

Ponavljanje rezervacije je obično polje `select` u potvrdi, ispod pitanja: "Samo ovaj put" ili "Svake sedmice, N puta" (2 do 12). Nisam pravio kalendar s označavanjem sedmica, jer je pravilo uvijek isto (isti dan i sat), a padajuća lista radi tastaturom i na telefonu bez ičeg dodatnog. Dugme kaže koliko termina pravi ("Rezerviši 3 termina"). Kad neke sedmice nisu slobodne, greška ih nabroji po imenu dana, a polje ostane, da se može izabrati manje sedmica.

Plaćanje (Faza 11) ne uvodi nove boje, fontove ni razmake. "Plati 15 KM" je glavno tamno dugme, jer je to jedina stvar koju klijent tada treba uraditi, a cijena piše na dugmetu, da se zna koliko prije nego što se ode na Stripe. Iznad dugmeta je rok za uplatu sitnim sivim slovima. Potvrda plaćenog termina kaže cijenu i da se plaća karticom odmah poslije, a ponavljanja nema. Povratak sa Stripea je ista podebljana poruka kao poslije uspjeha, bez zelene i bez ikone. Kad se plaćena rezervacija otkazuje, ispod pitanja piše koliko se vraća.

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
