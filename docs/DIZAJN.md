# Dizajn

Smjer je **Mjed i emajl**, izabran 25.11.2026 za drugi redizajn u Fazi 12. Zamijenio je **Čelik i žar** (prvi redizajn, isti dan), koji je ispao ravan: jedna siva površina, 3 px zaobljenja na svemu, jedan font i jedna boja. Paleta je 26.11.2026 zamijenjena (Redizajn 4, vidi "Boje"): hladni porculan i teget tinta su djelovali staro, pa je dan sad topli kamen, a noć neutralan grafit. Ideja, potpisni elementi i tri spektakularna momenta su u [redizajn/smjer.md](redizajn/smjer.md).

Redizajn ide ekran po ekran: landing, kalendar i rezervacija, dashboard, ostalo. Boje, fontovi, veličine i oblik ispod već važe. Sekcije od "Forme i dugmad" naniže još opisuju prvi redizajn i prepisuju se kad dođe red na svaki ekran.

Pravilo: boja, font, razmak ili zaobljenje kojeg ovdje nema ne ulazi u UI. Ako nešto zatreba, prvo se dopiše ovdje, pa tek onda u kod. Vrijednosti u kodu su CSS varijable na jednom mjestu.

## Ideja

Kazandžiluk, ulica kazandžija na Baščaršiji: mjed koja se kuje čekićem i emajl kojim se boje značke. Termin je emajlirani žeton koji uzmeš s ploče, a rezervacija je karta s mjedenom folijom. Sve što se dodiruje ima debljinu. Mjed znači "tvoje", a četiri emajl boje razlikuju resurse.

## Boje

Dvije teme, svaka napravljena posebno: dan je topli kamen, noć je grafit. Do 26.11.2026 dan je bio hladan porculan (`#E7EAF1`), a noć zasićena teget tinta (`#121735`). Uz mjed i emajl je to bilo previše plave, mjed na sivo-plavoj je izgledala kao senf, a teget noć kao dashboard od prije pet godina. Pogledao sam šta danas rade proizvodi tog tipa: skoro bijela topla podloga, neutralna skoro crna noć, jedna jaka akcent boja i tanka ivica oko ploča umjesto teških sjena. Mjed je zato svjetlija i čistija, a noć nema boje, pa su mjed i emajl jedine boje na ekranu. Od 26.11.2026 tamna tema je podrazumijevana za svakoga, bez obzira na sistem, jer je na grafitu mjed najljepša. Svijetla je izbor u zaglavlju i browser je pamti. Kontrast je po WCAG formuli za relativnu luminansu (AA: 4,5 za tekst, 3 za ivice i oblike).

| Varijabla | Hex | Uloga | Kontrast |
|---|---|---|---|
| `--dan-podloga` | `#F4F2EE` | dan: topli kamen, pozadina | |
| `--dan-povrsina` | `#FAF9F7` | dan: ploče, polja | |
| `--dan-izdignuto` | `#FFFFFF` | dan: žeton, karta, meni | |
| `--dan-tekst` | `#18181B` | dan: tinta, sav tekst; i tekst na mjedi u obje teme | 15,85 na podlozi, 17,72 na izdignutom |
| `--dan-sporedni` | `#5C5A63` | dan: napomene | 6,06 na podlozi, 6,78 na izdignutom |
| `--dan-greska` | `#C2262E` | dan: greška, uvijek uz ikonu i tekst | 5,20 na podlozi |
| `--dan-mjed-tekst` | `#8A5A00` | dan: mjed kao tekst (link "moje") | 5,30 na podlozi, 5,93 na izdignutom |
| `--noc-podloga` | `#0D0E11` | noć: grafit, pozadina | |
| `--noc-povrsina` | `#15161A` | noć: ploče, polja | |
| `--noc-izdignuto` | `#1D1F24` | noć: žeton, karta, meni | |
| `--noc-tekst` | `#EDECEF` | noć: sav tekst | 16,40 na podlozi, 14,01 na izdignutom |
| `--noc-sporedni` | `#A1A0AA` | noć: napomene | 7,47 na podlozi, 6,38 na izdignutom |
| `--noc-greska` | `#FF8A80` | noć: greška | 8,45 na podlozi |
| `--mjed` | `#E6A93F` | dan: moje, glavno dugme, folija | tinta na mjedi 8,53; mjed na podlozi 1,86, zato nikad tekst |
| `--mjed-noc` | `#F0BC5E` | noć: moje, i mjed kao tekst | tinta na mjedi 10,17; na noćnoj podlozi 11,08 |
| `--mjed-sjaj` | `#FCE6B4` | svijetla strana metalnog preliva | |
| `--mjed-sjena` | `#A36F12` | tamna strana metalnog preliva | |
| `--emajl-sipak` | `#E5484D` | resurs, kao površina ili rub | 3,50 na dnevnoj podlozi |
| `--emajl-safran` | `#F2B530` | resurs, kao površina ili rub | tinta na šafranu 9,64 |
| `--emajl-tirkiz` | `#12A594` | resurs, kao površina ili rub | 2,75 na dnevnoj podlozi, zato uvijek s tintanim rubom |
| `--emajl-lapis` | `#3E6AE6` | resurs, kao površina ili rub | 4,26 na dnevnoj podlozi |

Emajl nikad nije tekst i nikad jedini znak stanja: boja kaže koji je resurs, a oblik i tekst kažu stanje. Mjed je samo za "moje" i glavnu akciju. Greška u noći je svijetlo crvena, da ne potone u tintu.

Senke su od boje teksta (`--sjena-boja`, u noći `--sjena-noc`), mekše nego prije, i imaju tri nivoa: `--dubina-1` (ploča leži), `--dubina-2` (žeton stoji), `--dubina-3` (karta lebdi). Svaki nivo ima tanak svijetli gornji rub (`--odsjaj`), kao rub izdignute ploče, i ivicu od 1 px (`--obris`, 8 % boje teksta) oko cijele ploče. Na grafitu se sjena skoro ne vidi, pa oblik drži ivica. Udubljeno (zauzet termin, polje) je `--utisnuto`. Gradijent postoji kao `--metal`, na mjedi i foliji, gdje glumi metal, i kao dva meka sjaja na landingu: iza prozora s demom (mjed i lapis, jedino mjesto gdje se dvije boje miješaju) i na vrhu tamne trake "Kako radi".

## Fontovi

**Hanken Grotesk** (`@fontsource-variable/hanken-grotesk`, težina 100 do 900) je jedini font za naslove, tekst, cifre i dugmad (Redizajn 3, 25.11.2026). Naslovi su 700, normalne širine, s razmakom slova -0,02em (`--tezina-naslov`, `--razmak-slova-naslov`) i proredom 1,15. Cifre vremena su `tabular-nums`, pa se 08:00 i 11:00 poravnaju. Anybody s osom širine i slova koja se šire prema kursoru su izbačeni: upadali su u oči, ali nisu djelovali ozbiljno. Latin-ext (č, ć, š, đ, ž) Hanken ima u posebnom podskupu koji browser skine samo kad treba. **IBM Plex Mono** je samo na karti (`--font-karta`), kao otisak.

Fontova su tačno dva: Hanken Grotesk za sve i IBM Plex Mono za kartu i demo lozinke. Archivo je bio ostao u `package.json` iz prvog redizajna, iako se nigdje nije učitavao, pa je izbačen 26.11.2026. Latin-ext (č, ć, š, đ, ž) imaju oba, provjereno 25.11.2026 po fajlovima `*-latin-ext-*.woff2` u paketima.

Veličine teksta. Osnova je 16 px, ispod 14 px se ne ide:

| Varijabla | Veličina | Gdje |
|---|---|---|
| `--velicina-sitno` | `14px` | napomene, oznake stanja |
| `--velicina-tekst` | `16px` | sav ostali tekst, dugmad, polja forme |
| `--velicina-vrijeme` | `20px` | vrijeme na žetonu |
| `--velicina-naslov-3` | `20px` | naslov dana, podnaslov, naslov pločice |
| `--velicina-naslov-2` | `26px` | naslov dijela ekrana |
| `--velicina-naslov-1` | `34px` | naslov ekrana, jedan po ekranu |
| `--velicina-naslov-0` | `44px` | naslovi sekcija na landingu, velike brojke na dashboardu |
| `--velicina-hero` | `clamp(38px, 5vw, 64px)` | samo naslov u heru landinga |

Prored je 1,55 za tekst i 1 za naslove: široka teška slova s većim proredom izgledaju kao dva odvojena reda.

## Razmaci i oblik

Razmaci su na mreži od 4 px (4, 8, 12, 16, 24, 32, 48, 64, 96). Žeton je visok najmanje 48 px. Zaobljenje raste s nivoom, nije isto na svemu: polje i dugme 10 px (`--zaobljenje`), žeton 14 px (`--zaobljenje-zeton`), karta 20 px (`--zaobljenje-karta`), ploča 28 px (`--zaobljenje-ploca`), a pilula (prekidač teme, oznaka) puna (`--zaobljenje-puno`). Podloga ima zrno (`--zrno`) koje se jedva vidi, kao dobar papir.

## Pokret

Odgovor na akciju traje 160 do 260 ms (`--trajanje-brzo`, `--trajanje-srednje`), karta 420 ms (`--trajanje-karta`), kriva `--kriva-izlaz`. Animira se samo `transform` i `opacity`. Tri spektakularna momenta (hero, uzimanje termina, prelazi između ekrana) su opisana u `redizajn/smjer.md`, a sve ostalo je mirno. `prefers-reduced-motion` gasi letove, nagib i prelaze, a stanja ostaju.

Na landingu (26.11.2026) ima još pet malih pokreta, svi ugašeni uz `prefers-reduced-motion`. Kartica ima reflektor: ispod miša je blaga mjedena svjetlost (18 % mjedi, krug od 340 px), ispod teksta, pa ne kvari kontrast. Položaj daje `landing/reflektor.js`, jedan slušač na cijeloj stranici, a prst ga ne pali. Naslovi sekcija, kartice, pitanja, demo i završni poziv izrone (32 px odozdo, `scale` 0,97 do 1) kad uđu u ekran. Prozor s demom se udalji (do `scale` 0,92 i 35 % vidljivosti) dok hero izlazi s ekrana. Na vrhu je mjedena linija od 3 px koja raste sa skrolom. Ova tri su CSS `animation-timeline` (`view()` i `scroll()`), bez JavaScripta. Gdje ga browser ne zna (Firefox za sada), sve je samo vidljivo, a linije napretka nema. Animira se `translate`, `scale` i `opacity`, ne `transform`, jer `transform` već koristi hover kartice, pa bi se svađali. Peto: preko mjedenog dugmeta na hover jednom pređe traka svjetla (`--trajanje-odsjaj`, 700 ms), ispod teksta. Vraća se bez animacije, da ne ide unazad.

Prelaz između ekrana ide preko `tema/PrelazLink.jsx`: link koji navigaciju pokrene kroz `uzPrelaz` s `data-prelaz="ekran"`. Stari ekran se malo digne i izblijedi, novi dođe odozdo, a naziv organizacije ili resursa preleti s pločice u naslov sljedećeg ekrana (ime prelaza `org-<id>` i `resurs-<id>`). Nazivi ostalih pločica, koji na novom ekranu nemaju par, odu za `--trajanje-brzo`, da ne vise preko novog ekrana. Koriste ga pločice organizacija i resursa, veza nazad iz kalendara i linkovi u zaglavlju. React Router ima `viewTransition` na linku samo uz data router, a mi imamo `BrowserRouter`, pa je ovo petnaest linija umjesto prelaska na drugi router. Ctrl, Shift i srednji klik ostaju browseru, a bez podrške u browseru je to običan link. Dugme nazad u browseru nema prelaz.

## U kodu

Sve vrijednosti su u `frontend/src/tokeni.css`. Primitive su imenovane po temi (`--dan-*`, `--noc-*`, `--mjed*`, `--emajl-*`), a komponente koriste samo semantičke varijable (`--podloga`, `--povrsina`, `--izdignuto`, `--tekst`, `--tekst-sporedni`, `--moje`, `--greska`, `--linija`...), koje tema prebaci. Test `tokeni.test.js` pada kad se hex vrijednosti u `tokeni.css` razlikuju od tabele u ovom fajlu, kad neki drugi fajl u `frontend/src` napiše boju direktno (hex, `rgb()`, `hsl()`) i kad se veličine teksta ne slažu s tabelom ili se `font-size` piše brojem van `tokeni.css`.

## Forme i dugmad

Polje je udubljeno u ploču: pozadina je `--podloga`, s unutrašnjom sjenom odozgo i ivicom od 1 px u `--tekst-sporedni`. Ivica polja treba kontrast 3:1, a sporedna boja ima 6,06:1 na dnevnoj podlozi i 6,38:1 na noćnoj izdignutoj ploči. `--linija` bi bila preslaba. Na hover ivica postane `--tekst`. Neispravno polje dobije ivicu od 3 px u boji `--greska` i tekst greške ispod, pa se razlikuje i bez boje. Oznaka je iznad polja, nikad samo placeholder. Zaobljenje polja je `--zaobljenje` (10 px).

Dugme je pilula od tinte (`--tekst` s tekstom u `--podloga`, 15,85:1 danju), a noću obrnuto: svijetla pilula s tamnim tekstom. Ima sjenu `--dubina-2` i tanak svijetli rub gore, pa stoji iznad ploče. Na hover se digne za 1 px, a na klik spusti i sjena se skrati, sve za `--trajanje-brzo`. Dok čeka odgovor, blijedo je (60 %) i kaže šta radi ("Prijava u toku"). Glavna akcija ekrana je mjedena (`.dugme-mjed`, puna `--moje` s tekstom `--tekst-na-moje`, 8,53:1 danju i 10,17:1 noću). Od Redizajna 3 dugme nema metalni preliv: preliv `--metal` je samo na karti i na mom žetonu, da mjed ostane znak za "tvoje", a ne ukras: "Rezerviši" u karti i "Prijavi se". Jedna po ekranu. Veza na drugi ekran koja mora stajati kao dugme (Članovi i pozivnice, Novi resurs, Statistika) je pilula od površine s tankim rubom (`.veza-pilula`), da se ne miješa s akcijom. Prelaz koji je samo rečenica ("Napravi nalog" ispod prijave) ostaje podvučen tekst.

Greška cijele forme i greška polja imaju ikonu prekinutog kruga s uzvičnikom ispred teksta, jer se greška ne smije prepoznavati samo po boji (šipak iz emajla je blizu boje greške). Ikona je CSS maska u boji teksta, pa u SVG-u nema upisane boje. Greška forme je još i ploča s 8 % boje greške i trakom od 4 px lijevo. Poruka o uspjehu u formi je ista ploča, samo od površine i s trakom od tinte.

Trajanja su `--trajanje-brzo` (160 ms, odgovor na klik), `--trajanje-srednje` (260 ms, promjena stanja) i `--trajanje-karta` (420 ms, karta izlazi iz automata). Uz `prefers-reduced-motion` su svi prelazi i animacije 0 ms: stanje se i dalje promijeni, samo bez pokreta.

Organizacije i resursi su pločice u mreži (`minmax(280px, 1fr)`), izdignute (`--dubina-2`), svaka sa svojom emajl bojom: kvadrat od 28 px gore lijevo i rub od 4 px odozdo. Boja dolazi iz ID-ja (`resursi/emajl.js`), pa je resurs iste boje i na listi i u svom kalendaru. Naziv je u fontu naslova, `--velicina-naslov-3`, a uloga ili trajanje sitno ispod. Cijela pločica je link, na hover se digne za 2 px i podvuče naziv. Kod resursa koji korisnik vodi, "Raspored" i "Uredi" su podvučene veze na dnu iste pločice. Članovi i izuzeci su zbijene pločice (`.tabla-zbijena`), jer ih ima više i ne vode nigdje: emajl je tačka od 20 px lijevo, ime i email jedno ispod drugog, a uloga ili "Ukloni" ispod. Izuzeci su svi šipak, jer izuzetak znači da resurs ne radi. Podaci o organizaciji (uloga, zona) su pilule ispod naslova.

Kalendar je jedan dan kao ploča sa žetonima, i na telefonu i na desktopu (detalji u "Stanja termina"). Ispod naslova resursa su pilule s onim što se pita prije izbora: trajanje, cijena ili "besplatno", i broj mjesta kad ih je više od jednog. Uz naslov je pločica emajl boje resursa. Kostur dok se termini učitavaju su prazni žetoni koji polako "dišu" (`--trajanje-disanje`), a uz `prefers-reduced-motion` stoje mirno. Na desktopu je karta desno i prikovana ispod zaglavlja, a na telefonu je, dok je termin izabran, prikovana za dno ekrana kao ploča sa zaobljenim gornjim uglovima.

Desno od ploče (na desktopu) je karta, potpisni element iz `docs/redizajn/smjer.md`, prikovana ispod zaglavlja dok se ploča skrola. Dok termin nije izabran, na njenom mjestu je isprekidan obris s rečenicom šta će se tu pojaviti. Izabran žeton odleti u kupon karte u nastajanju (View Transition, `tema/prelaz.js`), a karta pokaže dan, put od početka do kraja, resurs, ponavljanje i mjedeno dugme "Rezerviši", koje dok čeka server kaže "Rezervišem..." i zaključa se. Na telefonu je karta ispod ploče, a dok je termin izabran prikovana je za dno ekrana. Tamo nema naziva resursa, jer je naziv već naslov stranice.

Karta je izdignuta (`--dubina-3`), sa zaobljenjem `--zaobljenje-karta`, mjedenom trakom na vrhu i folijom: preliv koji klizi kad se karta nagne prema kursoru ili prstu, slabiji noću (`--folija-jacina`). Zarezi perforacije su izrezani maskom, pa se kroz njih vidi šta je ispod karte. Zaglavlje je u `--font-karta` (IBM Plex Mono): "Karta № 1DDE-8D24", gdje je broj prvih osam znakova ID-ja rezervacije u dva dijela, a kod karte u nastajanju "Nova karta". Put od-do su dva vremena i pruga s dvije stanice. Desno je kupon od 116 px (108 px na telefonu), odvojen isprekidanom perforacijom. Na kuponu je žig: stanje riječima u dvostrukom okviru, nakrivljen 8 stepeni ("potvrđena", "čeka potvrdu", "čeka uplatu"). Kad server potvrdi rezervaciju, karta izađe iz automata: otkrije se odozgo prema dole i klizne na mjesto za `--trajanje-karta`, a folija jednom prođe svjetlom. Uz `prefers-reduced-motion` karta se samo pojavi i ne naginje se. Karta je HTML, ne slika, pa je čita i čitač ekrana ("Karta 1DDE-8D24").

Potvrda rezervacije nije modal, nego karta u nastajanju: pitanje, ponavljanje i dugmad su u njoj, pored ploče s koje je termin izabran. Raspored providera je lista izdignutih ploča sa stanjem kao trakom lijevo (puna mjed za potvrđenu, isprekidana za onu na čekanju), s imenom klijenta, a ispod su dugmad za promjenu. Glavna akcija (Potvrdi, Upiši dolazak) je puno dugme, a ona koja se ne može vratiti (Odbij, Upiši nedolazak) je tekst-dugme i traži drugi klik. Prethodni i sljedeći dan su pilule, iste kao mjeseci u statistici.

Moje rezervacije su karte (iste kao u kalendaru) u mreži od dvije kolone (`minmax(440px, 1fr)`, na telefonu jedna). Najbliži termin je prva karta, preko cijelog reda do 720 px, s vremenima od 44 px, i jedina se naginje prema kursoru ili prstu: to je karta s kojom se ide na ulaz. Prošle i otkazane leže na stolu: bez sjene, bez folije, siva traka umjesto mjedene. Stanje je žig na kuponu, riječima, a uz njega i oblik: potvrđena ima mjedeni žig, na čekanju tijelo karte ima isprekidan okvir u `--tekst-mjed`, otkazana, odbijena i istekla imaju sivi tekst, precrtana vremena i žig u boji greške, a završena ima rupu kroz kupon, kao od kondukterskih kliješta. "Otkaži" i "QR kod za dolazak" stoje u jednom redu, a pitanje za otkazivanje i QR se otvore ispod, preko cijele širine. QR je uvijek na bijeloj ploči s tamnim modulima, i u tamnoj temi, jer kamera traži kontrast. Uz potvrđenu nadolazeću kartu je "Sačuvaj kartu kao sliku". Folija je noću slabija (`--folija-jacina` 0,28 umjesto 0,55), jer isti preliv na tamnoj karti udara duplo jače. Raspored (za onoga ko vodi resurs) i lista čekanja su izdignute ploče, s mjedenom trakom lijevo za potvrđenu (puna) i onu na čekanju (isprekidana).

Poruka poslije uspjeha je podebljan tekst ispod liste, bez ikone i zelene boje, jer zelene u paleti nema.

Moj nalog: ime i email su dvije pilule s oznakom iznad, a kalendar na telefonu, izvoz podataka i brisanje naloga su tri ploče jedna ispod druge. Brisanje ima traku u boji greške lijevo. Link za kalendar je obično polje samo za čitanje: cijeli link se označi kad polje dobije fokus, pa se može kopirati i kad dugme "Kopiraj link" ne radi. Puno dugme je samo jedno: "Napravi link" dok linka nema, a "Kopiraj link" odmah poslije. "Napravi novi link" i "Ugasi link" su sporedne radnje, podvučen tekst.

Grupni termin (više mjesta) ima isti red kao običan, a oznaka kaže koliko je mjesta ostalo: "slobodno, još 3 mjesta" ili "još 1 mjesto". Pun grupni termin piše "popunjeno" i ima istu šrafuru kao zauzet, jer "zauzeto" zvuči kao da ga je uzela jedna osoba. Broj mjesta je samo tekst, bez nove boje ili ikone.

QR kod za dolazak se otvara ispod potvrđene karte, na klik na podvučen tekst "QR kod za dolazak", isto kao otkazivanje. Uvijek je na bijeloj ploči s tamnim modulima (`--dan-*`), i u tamnoj temi, jer kamera traži kontrast i tihu zonu oko koda. Kod je širok do 200 px, a na širem ekranu je objašnjenje desno od njega. Stranica koju provider otvori iz koda ima samo objašnjenje i jedno dugme "Upiši dolazak".

Forma koja stoji sama na ekranu (nalog, članovi, resurs, radno vrijeme, izuzeci) je izdignuta ploča do 640 px, a polja su udubljena u nju. Forma u prijavi je već u svojoj ploči. Pravilo je `section > .forma`, pa ne treba nova klasa na svakoj formi.

## Prijava i registracija

Odjavljen korisnik na `/` dolazi na landing, a prijava i registracija su na svojim adresama. Obje imaju dvije ploče. Lijevo je tamna ploča, uvijek noćna i u svijetloj temi, kao noć pomjeranja sata na landingu: gore "Termin je tvoj." u boji teksta, bez preliva, pa rečenica šta je SlotBook i primjer karte za sutra od 10 do 11, da ne izgleda kao stara zaboravljena rezervacija. Desno je forma na izdignutoj ploči, s mjedenim dugmetom preko cijele širine. Na telefonu je prvo forma, pa tamna ploča, da se prijava vidi bez skrolanja. Na užem od 480 px ploča ima manji unutrašnji razmak, a karta nema red s mjestom, da pruga između dva vremena ostane vidljiva.

Na demu (profil `demo` na backendu) je ispod forme dio "Probaj bez naloga": tri demo računa kao žetoni s emajl rubom odozdo (lapis, tirkiz, šipak), u svakom ime podebljano i uloga sitnim slovima, a ispod lozinka u `--font-karta`. Cijeli žeton je dugme. Van dema se ne vidi ništa, ni dok se učitava.

## Učitavanje, greška, prazno

Učitavanje je na svim ekranima isto: tri izdignute pločice različite visine koje polako "dišu" jedna za drugom (`Ucitava`), u obliku liste koja će stići, a čitač ekrana čuje šta se učitava ("Učitavaju se tvoje rezervacije"). Kalendar ima svoj kostur od praznih žetona. Uz `prefers-reduced-motion` stoje mirno.

Greška učitavanja (`NijeUcitano`) je izdignuta ploča s trakom u boji `--greska` lijevo, ikonom i rečenicom šta se nije učitalo, i dugmetom "Pokušaj ponovo". Traka je samo na ploči, ne i na poruci unutra.

Prazno stanje je isprekidan obris (`--linija-jaka`, 2 px) sa zaobljenjem karte, isti kao prazna karta u kalendaru, s udubljenim otiskom žetona koji tu fali, rečenicom šta se može dalje i, gdje ima smisla, linkom ili dugmetom. Tako izgleda i stranica koja ne postoji.

Radnja koja se ne može vratiti ("Obriši nalog") je ista pilula kao dugme, ali u boji `--greska` (5,20:1 danju, 8,45:1 noću), da se ne zamijeni s glavnom radnjom. Dok se šalje, blijeda je kao svako dugme.

Statistika ima četiri brojke na vrhu (rezervacije, popunjenost, održane i potvrđene, nedolasci), sve izračunate iz istog odgovora kao tabele ispod, pa broj gore i broj u tabeli ne mogu reći različito. Popunjenost je jedina na mjedi, jer je to glavni broj za onoga ko vodi prostor. Ispod su ploče: po stanju i popunjenost jedna pored druge, pa "Kad je gužva" i najtraženiji termini. Trake su udubljen žlijeb s punim dijelom koji naraste slijeva kad stignu podaci (700 ms, bez toga uz smanjen pokret). Po stanju nose značenje kao kalendar: potvrđena i završena su mjed, ono što se nije desilo (otkazana, odbijena, istekla, nedolazak) je šrafura, a na čekanju tinta. "Kad je gužva" je mreža dan × sat u jednoj boji: što više rezervacija, to više mjedi (od 28 % do 100 %, `color-mix` u oklab), a sat bez ijedne je udubljen. Noću to znači svjetlije, jer mjed sija na tinti. Mreža je prava tabela s brojem u svakoj ćeliji za čitač ekrana, a oku broj kaže opis pri prelazu mišem. Ćelije se pojave kolonu po kolonu. Broj uvijek piše i u tabeli, pa boja nije jedini nosilac podatka.

Ikona u tabu (`public/favicon.svg`) je isti mjeden žeton s prorezom kao znak u zaglavlju (`tema/Znak.jsx`). SVG u tabu ne čita CSS varijable, pa su tu hex vrijednosti iz tabele boja (`--mjed-sjaj`, `--mjed`, `--mjed-sjena`, `--dan-tekst`), a `tokeni.test.js` gleda samo `src/`.

Nije urađeno: polja za vrijeme (`input type=time`) i dalje pokazuju AM/PM kad je browser na engleskom. Format bira browser po svom jeziku, ne po `lang="bs"`, a zamjena drugom kontrolom bi bila promjena ponašanja, ne dizajna.

## Fokus

Fokus je okvir od 3 px u boji `--tekst`, odmaknut 3 px od elementa (`--fokus`, `--fokus-odmak`). WCAG traži najmanje 3:1 za okvir fokusa prema onome pored čega stoji. Zbog odmaka okvir stoji na ploči, ne na elementu: danju tinta ima 15,85:1 na podlozi i 17,72:1 na izdignutoj ploči, a noću svijetli tekst 16,40:1 na podlozi i 14,01:1 na izdignutoj. Na mjedenom žetonu bi noću imao samo 1,48:1, pa odmak tu nije ukras, nego razlog zašto se fokus vidi.

## Stanja termina

Termin je žeton u mreži na ploči (`.termini`, `auto-fill` od 148 px: dva u redu na telefonu, četiri na desktopu), a ne red u listi. Svako stanje se razlikuje i bez boje: po debljini, šrafuri, okviru, ikoni i tekstu. `--emajl` je boja resursa (`resursi/emajl.js`, iz ID-ja resursa), ista na pločici uz naslov, na izabranom danu i na rubu slobodnih žetona.

- **slobodno:** žeton stoji (`--dubina-2`), s mjedenim rubom od 3 px odozdo (do 26.11.2026 je rub bio u emajl boji resursa, ali zeleni rub nije pripadao ostatku stranice), prazan krug od 14 px u gornjem desnom uglu i riječ "slobodno". Hover ga digne 2 px, pritisak spusti 1 px;
- **izabran:** žeton odleti u kartu (View Transition, ime `zeton-leti`, `--trajanje-karta`), a na ploči ostaje obris od 2 px s punim krugom. U kuponu karte je isti žeton s riječi "biraš";
- **zauzeto:** udubljen (`--utisnuto`) u boji podloge, s tankom šrafurom (linija 1 px na 7 px), bez debljine, katanac od 14 px u gornjem desnom uglu i riječ "zauzeto". Vrijeme nije precrtano, nego u sporednoj boji i težini 600, jer se precrtano vrijeme teško čitalo;
- **tvoje:** mjeden žeton (`--metal`), ikona karte u uglu, tekst u `--tekst-na-moje` i riječ "tvoje";
- **tvoje na čekanju:** žeton s isprekidanim okvirom od 2 px u `--tekst-mjed` i riječ "čeka potvrdu" u istoj boji;
- **ponuđen tebi s liste čekanja** (Faza 10): kao slobodan, uz mjedenu traku lijevo (`--razmak-4`) i oznaku "ponuđen tebi danas do 17:30". Termin je tvoj samo do roka, pa nije cijeli od mjedi;
- **tvoje, čeka uplatu** (Faza 11): isto kao na čekanju, samo oznaka kaže "čeka uplatu";
- **zauzeto, a na listi sam:** isto kao zauzeto, uz tekst "zauzeto, na listi si".

Dani su udubljena traka, a izabran dan se digne iz nje kao žeton s mjedenim rubom. Emajl boja resursa ostaje na pločici uz naslov resursa i na listi resursa. Dan s mojim terminom ima mjedenu tačku ispod broja. Strelice idu kroz slobodne žetone redom: lijevo i gore na prethodni, desno i dolje na sljedeći.

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
- gradijente, sjaj, staklo (glassmorphism) i mutnu pozadinu. Izuzeci su nabrojani gore: metal na mjedi, dva sjaja na landingu, reflektor na kartici i odsjaj preko mjedenog dugmeta. Svaki prati pokret ili materijal, nijedan nije pozadina;
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

## Landing (Redizajn 3)

Landing ima osam dijelova, svaki odgovara na jedno pitanje posjetioca. Hero: naslov "Termini za čitaonice, teretane i konsultacije", jedna rečenica, dugmad "Probaj demo" (mjed) i "Napravi organizaciju" (sporedno, obris), a desno pravi mini kalendar u okviru prozora aplikacije. Ispod je traka pilula "za koga" s emajl tačkama, pa prije i poslije (lijevo udubljena ploča s crticama, desno izdignuta s mjedenim kvačicama, pa se razlikuju i bez boje), pa "Kako radi" u tri koraka, jedina tamna traka preko cijele širine, u obje teme (grafit `--noc-povrsina` s blagim mjedenim sjajem na vrhu), da stranica ima kontrast u sredini. Slijedi šest mogućnosti s ikonama crtanim rukom (`landing/Ikona.jsx`, linija 1,75 px na mreži 24, mjed na pločici od 14 % mjedi s tankim mjedenim rubom; lista čekanja je red sa strelicom kod prvog), tri kartice "Na ovo se možeš osloniti" (sudar, pomjeranje sata, odvojene organizacije, bez statusnih kodova i imena testova), česta pitanja kao `<details>`, ulaz u demo, završni poziv i mala sekcija "Kako je napravljeno", poslije koje nema praznog prostora (`main` na landingu nema donji razmak), sa sporednim dugmetom od 40 px i sitnim slovima. Pokret: hero tekst i prozor se pojave jednom (500 i 600 ms), let žetona u demou i animacija sudara na klik. Lenis ostaje. Bez cjenovnika, citata, brojki korisnika i logotipa.

Naslovi sekcija su centrirani i najviše 24ch široki. Mreže kartica uvijek imaju pune redove (šest mogućnosti u 1, 2 ili 3 kolone), a u karticama oslonca su ilustracija, naslov i tekst na srednjoj osi, s ilustracijama iste visine. U opisu kartice smije biti jedna fraza u boldu, bez druge boje.

Sve kartice na landingu (`.kartica`) imaju isto ponašanje, u duhu žetona: na hover, ili kad je fokus tastature u kartici (`:has(:focus-visible)`), kartica se digne 4 px, sjena pređe na `--dubina-3`, preko vrha klizne mjedeni odsjaj, a znak u kartici se malo pomjeri. Na pritisak se spusti (`scale(0.98)`, kraća sjena). Trajanje je `--trajanje-srednje` s `--kriva-izlaz`, a animiraju se samo transform, opacity i sjena. Uz smanjen pokret ostaje samo promjena sjene. Kartica sama ne prima fokus, jer na nju nema šta kliknuti; fokus dobija dugme u njoj.

Česta pitanja su blok od 720 px, svako pitanje u svojoj ploči. Otvoreno pitanje je izdignuto, s mjedenom linijom lijevo, a plus se okrene u minus. Odgovor se otvara glatko preko `::details-content` gdje browser to podržava, drugdje se samo pojavi.

Hero ulazi redom (naslov, rečenica, dugmad, napomena, svaki 90 ms poslije prethodnog), a prozor s demom stiže zadnji, podignut, s mekom dugom sjenom i blagim mjedenim sjajem iza. U pozadini hera je mreža termina od 112 × 48 px koja se gubi prema dnu.

Tekst na landingu je za vlasnika čitaonice i za studenta: naslov do osam riječi, opis jedna rečenica, dugme kaže šta se desi. Tehnički dokazi (409, EXCLUDE, testovi) su u README-u i `docs/`, ne na stranici.
