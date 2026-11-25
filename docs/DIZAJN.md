# Dizajn

Smjer je **Mjed i emajl**, izabran 25.11.2026 za drugi redizajn u Fazi 12. Zamijenio je **Čelik i žar** (prvi redizajn, isti dan), koji je ispao ravan: jedna siva površina, 3 px zaobljenja na svemu, jedan font i jedna boja. Ideja, potpisni elementi i tri spektakularna momenta su u [redizajn/smjer.md](redizajn/smjer.md).

Redizajn ide ekran po ekran: landing, kalendar i rezervacija, dashboard, ostalo. Boje, fontovi, veličine i oblik ispod već važe. Sekcije od "Forme i dugmad" naniže još opisuju prvi redizajn i prepisuju se kad dođe red na svaki ekran.

Pravilo: boja, font, razmak ili zaobljenje kojeg ovdje nema ne ulazi u UI. Ako nešto zatreba, prvo se dopiše ovdje, pa tek onda u kod. Vrijednosti u kodu su CSS varijable na jednom mjestu.

## Ideja

Kazandžiluk, ulica kazandžija na Baščaršiji: mjed koja se kuje čekićem i emajl kojim se boje značke. Termin je emajlirani žeton koji uzmeš s ploče, a rezervacija je karta s mjedenom folijom. Sve što se dodiruje ima debljinu. Mjed znači "tvoje", a četiri emajl boje razlikuju resurse.

## Boje

Dvije teme, svaka napravljena posebno: dan je hladan porculan, noć je tamna tinta. Tema prati sistem, a u zaglavlju se može izabrati ručno. Kontrast je po WCAG formuli za relativnu luminansu (AA: 4,5 za tekst, 3 za ivice i oblike).

| Varijabla | Hex | Uloga | Kontrast |
|---|---|---|---|
| `--dan-podloga` | `#E7EAF1` | dan: porculan, pozadina | |
| `--dan-povrsina` | `#F6F7FA` | dan: ploče, polja | |
| `--dan-izdignuto` | `#FFFFFF` | dan: žeton, karta, meni | |
| `--dan-tekst` | `#151933` | dan: tinta, sav tekst; i tekst na mjedi u obje teme | 14,31 na podlozi, 17,23 na izdignutom |
| `--dan-sporedni` | `#4B5172` | dan: napomene | 6,41 na podlozi, 7,72 na izdignutom |
| `--dan-greska` | `#B42318` | dan: greška, uvijek uz ikonu i tekst | 5,46 na podlozi |
| `--dan-mjed-tekst` | `#7A5A12` | dan: mjed kao tekst (link "moje") | 5,29 na podlozi, 6,37 na izdignutom |
| `--noc-podloga` | `#121735` | noć: tinta, pozadina | |
| `--noc-povrsina` | `#1A2045` | noć: ploče, polja | |
| `--noc-izdignuto` | `#232A56` | noć: žeton, karta, meni | |
| `--noc-tekst` | `#ECEEF8` | noć: sav tekst | 15,13 na podlozi, 11,81 na izdignutom |
| `--noc-sporedni` | `#A4AACB` | noć: napomene | 7,66 na podlozi, 5,98 na izdignutom |
| `--noc-greska` | `#FF8A80` | noć: greška | 7,67 na podlozi |
| `--mjed` | `#C9A04E` | dan: moje, glavno dugme, folija | tinta na mjedi 7,07; mjed na podlozi 2,02, zato nikad tekst |
| `--mjed-noc` | `#D9B566` | noć: moje, i mjed kao tekst | tinta na mjedi 8,82; na noćnoj podlozi 8,96 |
| `--mjed-sjaj` | `#F3DFA6` | svijetla strana metalnog preliva | |
| `--mjed-sjena` | `#8A6A24` | tamna strana metalnog preliva | |
| `--emajl-sipak` | `#E5484D` | resurs, kao površina ili rub | 3,25 na dnevnoj podlozi |
| `--emajl-safran` | `#F2B530` | resurs, kao površina ili rub | tinta na šafranu 9,38 |
| `--emajl-tirkiz` | `#12A594` | resurs, kao površina ili rub | 2,55 na dnevnoj podlozi, zato uvijek s tintanim rubom |
| `--emajl-lapis` | `#3E6AE6` | resurs, kao površina ili rub | 3,95 na dnevnoj podlozi |

Emajl nikad nije tekst i nikad jedini znak stanja: boja kaže koji je resurs, a oblik i tekst kažu stanje. Mjed je samo za "moje" i glavnu akciju. Greška u noći je svijetlo crvena, da ne potone u tintu.

Senke su od tinte, ne od crne (`--sjena-boja`, u noći `--sjena-noc`), i imaju tri nivoa: `--dubina-1` (ploča leži), `--dubina-2` (žeton stoji), `--dubina-3` (karta lebdi). Svaki nivo ima i tanak svijetli gornji rub (`--odsjaj`), kao rub izdignute ploče. Udubljeno (zauzet termin, polje) je `--utisnuto`. Gradijent postoji samo kao `--metal`, na mjedi i foliji, gdje glumi metal.

## Fontovi

**Anybody** (`@fontsource-variable/anybody`, širina 50 do 150 %, težina 100 do 900) je za naslove i velike cifre. Naslovi su široki (`--sirina-naslov`, 128 %), cifre vremena uske i visoke (`--sirina-brojevi`, 82 %, `tabular-nums`). Na landingu širina i težina slova prate kursor. **Hanken Grotesk** (`@fontsource-variable/hanken-grotesk`) je sav tekst, forme i dugmad. **IBM Plex Mono** je samo na karti (`--font-karta`), kao otisak.

Latin-ext (č, ć, š, đ, ž) imaju sva tri, provjereno 25.11.2026 po fajlovima `*-latin-ext-*.woff2` u paketima.

Veličine teksta. Osnova je 16 px, ispod 14 px se ne ide:

| Varijabla | Veličina | Gdje |
|---|---|---|
| `--velicina-sitno` | `14px` | napomene, oznake stanja |
| `--velicina-tekst` | `16px` | sav ostali tekst, dugmad, polja forme |
| `--velicina-vrijeme` | `20px` | vrijeme na žetonu |
| `--velicina-naslov-3` | `25px` | naslov dana, podnaslov |
| `--velicina-naslov-2` | `31px` | naslov dijela ekrana |
| `--velicina-naslov-1` | `44px` | naslov ekrana, jedan po ekranu |
| `--velicina-naslov-0` | `68px` | naslovi sekcija na landingu, velike brojke na dashboardu |
| `--velicina-hero` | `clamp(76px, 9.6vw, 148px)` | samo naslov u heru landinga |

Prored je 1,55 za tekst i 1 za naslove: široka teška slova s većim proredom izgledaju kao dva odvojena reda.

## Razmaci i oblik

Razmaci su na mreži od 4 px (4, 8, 12, 16, 24, 32, 48, 64, 96). Žeton je visok najmanje 48 px. Zaobljenje raste s nivoom, nije isto na svemu: polje i dugme 10 px (`--zaobljenje`), žeton 14 px (`--zaobljenje-zeton`), karta 20 px (`--zaobljenje-karta`), ploča 28 px (`--zaobljenje-ploca`), a pilula (prekidač teme, oznaka) puna (`--zaobljenje-puno`). Podloga ima fino zrno (`--zrno`), kao kucana mjed.

## Pokret

Odgovor na akciju traje 160 do 260 ms (`--trajanje-brzo`, `--trajanje-srednje`), karta 420 ms (`--trajanje-karta`), kriva `--kriva-izlaz`. Animira se samo `transform` i `opacity`. Tri spektakularna momenta (hero, uzimanje termina, prelazi između ekrana) su opisana u `redizajn/smjer.md`, a sve ostalo je mirno. `prefers-reduced-motion` gasi letove, nagib i prelaze, a stanja ostaju.

## U kodu

Sve vrijednosti su u `frontend/src/tokeni.css`. Primitive su imenovane po temi (`--dan-*`, `--noc-*`, `--mjed*`, `--emajl-*`), a komponente koriste samo semantičke varijable (`--podloga`, `--povrsina`, `--izdignuto`, `--tekst`, `--tekst-sporedni`, `--moje`, `--greska`, `--linija`...), koje tema prebaci. Test `tokeni.test.js` pada kad se hex vrijednosti u `tokeni.css` razlikuju od tabele u ovom fajlu, kad neki drugi fajl u `frontend/src` napiše boju direktno (hex, `rgb()`, `hsl()`) i kad se veličine teksta ne slažu s tabelom ili se `font-size` piše brojem van `tokeni.css`.

## Forme i dugmad

Polje je lim (`--povrsina`) s punom grafitnom ivicom od 1 px, jer ivica polja treba kontrast 3:1, a `--linija` ga nema. Neispravno polje dobije ivicu od 3 px u boji `--greska` i tekst greške ispod, pa se razlikuje i bez boje. Oznaka je iznad polja, nikad samo placeholder. Zaobljenje polja i dugmeta je `--zaobljenje` (3 px), a redovi i trake su ravni.

Glavno dugme je grafitna ploča (`--tekst`) sa svijetlim tekstom (`--povrsina`, 10,22:1) i donjom ivicom od uglja debljine `--ivica-dugmeta` (3 px), pa izgleda utisnuto. Na hover potamni u ugalj, a na klik se spusti za 1 px za `--trajanje-brzo` (150 ms), dok se donja ivica stanji, pa visina ostaje ista. Dok čeka odgovor, dugme je oksid (`--tekst-sporedni`) i kaže šta radi ("Prijava u toku"). Prelaz na drugi ekran (npr. "Napravi nalog" ispod prijave) je podvučen tekst, ne dugme.

Greška cijele forme i greška polja imaju ikonu prekinutog kruga s uzvičnikom ispred teksta, jer su `--greska` i žar bliske boje i greška se ne smije prepoznavati samo po boji. Ikona je CSS maska u boji teksta, pa u SVG-u nema upisane boje. Greška forme ima još debelu crvenu liniju lijevo i crveni tekst (5,42:1), bez pozadine.

Trajanja su `--trajanje-brzo` (150 ms, odgovor na klik), `--trajanje-srednje` (220 ms, promjena stanja) i `--trajanje-karta` (300 ms). Uz `prefers-reduced-motion` su svi prelazi i animacije 0 ms: stanje se i dalje promijeni, samo bez pokreta.

Liste (organizacije, resursi, članovi, izuzeci) su ploče lima, iste kao red termina: svaki red je `--povrsina`, a između redova je procjep od 2 px kroz koji se vidi podloga. Nema kartica, sjene ni linije iznad liste. Naziv je `--velicina-vrijeme` (20 px) i težak 700, jer se po njemu traži, a uloga ili trajanje su sitno desno u `--tekst-sporedni`. Red je visok najmanje `--red-min-visina`, cijela ploča je link i na hover podvlači naziv. Kod resursa koji korisnik vodi, "Raspored" i "Uredi" su podvučene veze na istoj ploči, desno.

Kalendar je jedan dan kao lista, i na telefonu i na desktopu. Iznad je traka sa sedam dana kao lenjir: dani su odvojeni tankim podiocima (`--linija`), ispod cijele trake je grafitna linija, a izabran dan ima debelu crtu ispod (`--razmak-4`) i podebljan tekst. Broj dana je u `--velicina-vrijeme`, težine 600. Dan u kojem imam termin (potvrđen ili na čekanju) ima tačkicu od žara od 8 px s prstenom od uglja, a čitač ekrana čuje "imaš termin". Ispod naslova resursa je jedan red s onim što se pita prije izbora: trajanje, cijena ili "besplatno", i broj mjesta kad ih je više od jednog. Termin je limena ploča visine `--red-min-visina`, s procjepom od 2 px do sljedeće: vrijeme lijevo u `--velicina-vrijeme`, težine 600 i s tabularnim ciframa, a desno oznaka stanja, cijena (samo kod plaćenog resursa i samo na terminu koji se može rezervisati, npr. "15 KM") i krug ili ikona. Šrafura zauzetog termina je `--linija` pod 135 stepeni, 4 px pruga na svakih 12 px, preko podloge, bez ploče, pa zauzet termin izgleda kao udubljenje. Kostur dok se termini učitavaju su četiri prazne ploče koje polako "dišu" (prozirnost od 1 do 0,5 za `--trajanje-disanje`, 1200 ms), a uz `prefers-reduced-motion` stoje mirno. Isključeno tekst-dugme (npr. "Prethodnih 7 dana" na prvoj sedmici) je sivo i bez podvlake.

Desno od liste (na desktopu od 900 px) je karta, potpisni element iz `docs/redizajn/smjer.md`. Lista i karta su u mreži `1fr` i 320 do 400 px, a karta je ljepljiva uz vrh dok se lista skrola. Dok termin nije izabran, na njenom mjestu je isprekidan obris (`--linija`, 2 px) s rečenicom šta će se tu pojaviti. Izabran termin otvara kartu u nastajanju: dan, put od početka do kraja, resurs, ponavljanje i dugme "Rezerviši", koje dok čeka server kaže "Rezervišem..." i zaključa se. Na telefonu je karta ispod liste, a dok je termin izabran prikovana je za dno ekrana (`position: sticky`), s grafitnom linijom iznad i podlogom ispod, pa ne gura listu. Tamo nema zaglavlja ni naziva resursa, jer je naziv već naslov stranice.

Karta je lim (`--povrsina`) s grafitnom ivicom od 1 px i zaobljenjem `--zaobljenje-karta`, bez sjene. Zaglavlje je u `--font-karta` (IBM Plex Mono): "Karta № 1DDE-8D24", gdje je broj prvih osam znakova ID-ja rezervacije u dva dijela, a kod karte u nastajanju "Nova karta". Ime aplikacije je prvo bilo i na karti, ali je na listi od šest karata to bilo šest puta isto, pa je skinuto. Put od-do su dva vremena u `--font-karta` veličine `--velicina-naslov-3` i pruga od 2 px s dvije stanice od 10 px. Desno je kupon od 112 px, odvojen isprekidanom perforacijom, sa zarezima gore i dolje (krugovi u boji podloge). Na kuponu je žig: stanje riječima u dvostrukom okviru od 3 px, nakrivljen 8 stepeni ("potvrđena", "čeka potvrdu", "čeka uplatu"). Kad server potvrdi rezervaciju, karta izađe iz automata: otkrije se odozgo prema dole i klizne 16 px na mjesto za `--trajanje-karta` (300 ms). To je jedini orkestriran pokret u aplikaciji. Uz `prefers-reduced-motion` karta se samo pojavi. Karta je HTML, ne slika, pa je čita i čitač ekrana ("Karta 1DDE-8D24").

Potvrda rezervacije nije modal, nego red odmah ispod izabranog termina, s tamnom linijom lijevo (`--razmak-4`, `--tekst`), isto kao greška forme, samo tamna. Pitanje je podebljano ("Rezervisati ponedjeljak, 19. novembar, 08:00 do 09:00?"), ispod su glavno dugme i "Odustani" kao tekst. Na telefonu se tako vidi bez skrolanja, a pored termina na koji se odnosi. Raspored providera koristi istu listu, samo umjesto resursa i organizacije piše ime klijenta, a ispod su dugmad za promjenu. Glavna akcija (Potvrdi, Upiši dolazak) je puno dugme, a ona koja se ne može vratiti (Odbij, Upiši nedolazak) je tekst-dugme i traži drugi klik.

Moje rezervacije su karte (iste kao u kalendaru), jedna ispod druge s razmakom `--razmak-16`, najviše 720 px široke. Na karti su broj, dan, put od-do, resurs, organizacija, pa napomene (rok za otkazivanje, rok za uplatu), "Otkaži", "Plati" i "QR kod za dolazak". Stanje je žig na kuponu, riječima, a uz njega i oblik: potvrđena ima žig od žara (ugalj na žaru, 4,82:1), na čekanju cijela karta ima isprekidanu ivicu, otkazana, odbijena i istekla imaju sivi tekst i precrtana vremena, a završena ima rupu kroz kupon (krug u boji podloge s tamnim rubom), kao od kondukterskih kliješta. Prvo je cijeli kupon potvrđene bio od žara, ali pet takvih karata jedna ispod druge je izgledalo kao niz narandžastih traka, pa je žar ostao samo na žigu. Animacija utiskivanja žiga "otkazana" iz skice nije urađena: otkazana karta se poslije osvježavanja liste ponovo napravi, pa bi se žig utiskivao i pri svakom otvaranju stranice. Raspored (za onoga ko vodi resurs) i lista čekanja su redovi lima kao termini u kalendaru, s trakom od žara lijevo za potvrđenu (puna) i onu na čekanju (isprekidana).

Poruka poslije uspjeha je podebljan tekst ispod liste, bez ikone i zelene boje, jer zelene u paleti nema.

Link za kalendar (Moj nalog) je obično polje samo za čitanje: cijeli link se označi kad polje dobije fokus, pa se može kopirati i kad dugme "Kopiraj link" ne radi. Tamno dugme je samo jedno: "Napravi link" dok linka nema, a "Kopiraj link" odmah poslije. "Napravi novi link" i "Ugasi link" su sporedne radnje, podvučen tekst. Dugme i sporedne radnje stoje u jednom redu s razmakom `--razmak-16`, a na uskom ekranu se prelome jedno ispod drugog. Nove boje, fontove ni razmake ovo ne uvodi.

Grupni termin (više mjesta) ima isti red kao običan, a oznaka kaže koliko je mjesta ostalo: "slobodno, još 3 mjesta" ili "još 1 mjesto". Pun grupni termin piše "popunjeno" i ima istu šrafuru kao zauzet, jer "zauzeto" zvuči kao da ga je uzela jedna osoba. Broj mjesta je samo tekst, bez nove boje ili ikone.

QR kod za dolazak se otvara ispod potvrđene rezervacije, na klik na podvučen tekst "QR kod za dolazak", isto kao otkazivanje. Moduli su `--tekst` na `--podloga`, s praznim okvirom od 4 modula, a kod je širok najviše `--sirina-forme`, pa na telefonu zauzme skoro cijelu širinu i kamera ga lako uhvati. Ispod je napomena sitnim sivim slovima. Stranica koju provider otvori iz koda ima samo objašnjenje i jedno tamno dugme "Upiši dolazak". Nove boje, fontove ni razmake ovo ne uvodi.

Forma je široka najviše `--sirina-forme` (400 px), da red za email na desktopu ne bude preko cijelog ekrana. Tu vrijednost sam potvrdio 15.11.2026, poslije koraka 4 Faze 7.

## Prijava kao početni ekran

Odjavljen korisnik na `/` dolazi na prijavu, pa je ona prvi ekran koji neko vidi. Nova ruta nije dodana. Na desktopu (od 900 px) je lijevo jedna rečenica šta je SlotBook, u fontu naslova i veličini `--velicina-naslov-1`, a ispod nje primjer karte za sutra od 10 do 11 (zato da ne izgleda kao stara zaboravljena rezervacija). Desno je forma. Na telefonu je prvo rečenica (`--velicina-naslov-3`), pa forma, a karta je ispod forme, da se prijava vidi bez skrolanja. To je odstupanje od skice u `docs/redizajn/smjer.md`, gdje je karta bila iznad forme. Registracija ima isti uvod. Na užem od 480 px kupon karte je 88 px, a vremena na karti `--velicina-vrijeme`, da se pruga između njih vidi.

Na demu (profil `demo` na backendu) je ispod forme dio "Probaj bez naloga": tri demo računa kao ploče iste vrste kao termini u kalendaru (lim, procjep od 2 px, cijeli red je dugme), u svakoj ime podebljano i uloga sitnim slovima, a ispod lozinka u `--font-karta`. Širok je kao forma. Van dema se ne vidi ništa, ni dok se učitava.

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

Termin je žeton u mreži na ploči (`.termini`, `auto-fill` od 148 px: dva u redu na telefonu, četiri na desktopu), a ne red u listi. Svako stanje se razlikuje i bez boje: po debljini, šrafuri, okviru, ikoni i tekstu. `--emajl` je boja resursa (`resursi/emajl.js`, iz ID-ja resursa), ista na pločici uz naslov, na izabranom danu i na rubu slobodnih žetona.

- **slobodno:** žeton stoji (`--dubina-2`), s emajl rubom od 3 px odozdo, prazan krug od 14 px u gornjem desnom uglu i riječ "slobodno". Hover ga digne 2 px, pritisak spusti 1 px;
- **izabran:** žeton odleti u kartu (View Transition, ime `zeton-leti`, `--trajanje-karta`), a na ploči ostaje obris od 2 px s punim krugom. U kuponu karte je isti žeton s riječi "biraš";
- **zauzeto:** udubljen (`--utisnuto`) i šrafiran, bez debljine, precrtano vrijeme i riječ "zauzeto";
- **tvoje:** mjeden žeton (`--metal`), ikona karte u uglu, tekst u `--tekst-na-moje` i riječ "tvoje";
- **tvoje na čekanju:** žeton s isprekidanim okvirom od 2 px u `--tekst-mjed` i riječ "čeka potvrdu" u istoj boji;
- **ponuđen tebi s liste čekanja** (Faza 10): kao slobodan, uz mjedenu traku lijevo (`--razmak-4`) i oznaku "ponuđen tebi danas do 17:30". Termin je tvoj samo do roka, pa nije cijeli od mjedi;
- **tvoje, čeka uplatu** (Faza 11): isto kao na čekanju, samo oznaka kaže "čeka uplatu";
- **zauzeto, a na listi sam:** isto kao zauzeto, uz tekst "zauzeto, na listi si".

Dani su udubljena traka, a izabran dan se digne iz nje kao žeton s emajl rubom. Dan s mojim terminom ima mjedenu tačku ispod broja. Strelice idu kroz slobodne žetone redom: lijevo i gore na prethodni, desno i dolje na sljedeći.

Karta u nastajanju (potvrda) nema foliju. Folija dođe tek kad server kaže da je rezervacija napravljena: karta se odštampa, folija jednom prođe svjetlom, i od tada se naginje prema kursoru ili prstu. Ispod je "Sačuvaj kartu kao sliku" (PNG, dvostruka gustina, `html-to-image` se učitava tek na klik). Dugme "Rezerviši" u karti je mjedeno, jer je to glavna akcija ekrana.

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
