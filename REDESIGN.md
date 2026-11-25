# SlotBook redesign: smjernica za Claude Code

Pročitaj ovaj fajl cijeli prije nego što išta promijeniš. Radimo redizajn izgleda i frontenda SlotBooka (React frontend, Spring Boot backend). Trenutni izgled je previše minimalistički i bezličan. Cilj je da aplikacija ostavi utisak na prvi pogled, da je zabavna za korištenje i da ima prepoznatljiv karakter, a da pritom ne izgleda kao još jedan generisani template.

## 1. Šta smiješ, a šta ne smiješ dirati

**Smiješ:**
- komponente, stilove, layout, fontove, boje, ikone, animacije
- strukturu JSX-a unutar komponenti, razbijanje velikih komponenti na manje
- dodati frontend biblioteke kad imaju jasnu svrhu (npr. `motion` za animacije, `@fontsource/*` za fontove)
- tekstove u UI-ju (copy), ako ih poboljšavaš po pravilima iz sekcije 7

**Ne smiješ:**
- dirati backend (Spring Boot), bazu, API endpointe ni oblik podataka koji frontend šalje i prima
- mijenjati poslovnu logiku: pravila zauzetosti termina, validaciju, auth tok, rute
- brisati funkcionalnost. Ako nešto postoji sad, postoji i poslije redizajna
- uvoditi novi framework (nema prelaska na Next.js, nema mijenjanja state managementa)

Ako ti neka promjena dizajna traži promjenu API-ja, stani i pitaj me.

## 2. Korak 0: upoznaj projekt (bez koda)

Prije bilo kakvih izmjena:

1. Pregledaj frontend i napiši mi kratak inventar:
   - koji ekrani postoje (npr. početna, izbor usluge, izbor termina, potvrda, login, admin/dashboard)
   - kako je trenutno riješeno stiliziranje (Tailwind, CSS moduli, obični CSS, UI biblioteka?)
   - koje su komponente zajedničke (dugmad, kartice, forme, modali)
   - šta se dešava kad nema podataka, kad se učitava i kad API vrati grešku
2. Pokreni aplikaciju i napravi screenshote svakog ekrana (desktop 1440px i mobitel 390px). Koristi Playwright ako je dostupan. To je "prije" stanje s kojim ćemo porediti.
3. Reci mi šta ti se čini najslabije u trenutnom izgledu, konkretno po ekranima.

## 3. Korak 1: plan dizajna (i dalje bez koda)

Predloži mi **dva ili tri različita smjera**, ne varijacije istog. Za svaki smjer daj:

- **Ideju u jednoj rečenici**: odakle dolazi karakter.
- **Boje**: 4 do 6 imenovanih hex vrijednosti i čemu svaka služi (pozadina, tekst, akcent, slobodan termin, zauzet termin, greška).
- **Tipografiju**: jedan ili dva fonta, uloga svakog, tipografska skala (veličine, težine, prored).
- **Layout**: ASCII skicu glavnog ekrana za rezervaciju, na desktopu i mobitelu.
- **Potpisni element**: jednu stvar po kojoj će se SlotBook pamtiti (vidi sekciju 4).
- **Kako se ponaša**: koje interakcije i animacije, i zašto baš te.

Onda **sam kritikuj svoje prijedloge**: ako neki dio izgleda kao nešto što bi napravio za bilo koju booking aplikaciju, promijeni ga i reci šta si promijenio. Tek kad ja izaberem smjer, krećeš s kodom.

## 4. Lični potpis

Ovo je najvažniji dio. SlotBook ne treba izgledati kao Calendly klon. Karakter treba proizaći iz onoga što aplikacija stvarno radi (vrijeme, termini, čekanje, dolazak na zakazano) i iz mjesta odakle dolazi.

Ideje od kojih možeš krenuti (ne moraš koristiti nijednu, ali ih uzmi kao primjer nivoa konkretnosti koji tražim):

- **Termin kao fizička stvar.** Dan nije tabela dugmića, nego traka vremena koju skrolaš ili vučeš, a termini su "komadi" koje uzimaš. Odabrani termin se vizuelno izdvoji iz trake, kao da si ga otkinuo.
- **Potvrda kao karta/listić.** Kad se rezervacija potvrdi, umjesto zelene kvačice i "Uspješno!" dobiješ listić s datumom, vremenom i uslugom, koji izgleda kao nešto što bi stavio u novčanik. Može se sačuvati kao slika ili dodati u kalendar.
- **Zenica kao izvor estetike.** Grad čelika: tamni metalni tonovi, utisnut (embossed) ili žigosan tekst, jedan topao užareni akcent koji se pojavljuje samo na odabranom terminu. Ako ideš ovim putem, neka bude suptilno i elegantno, a ne tematski park.
- **Ako je verzija za lokalnog brijača**: inspiracija iz stvarnog zanata (papirni blok za zakazivanje, ručno pisani raspored na ogledalu, tipografija starih natpisa na radnjama), ali bez klišea tipa brijačkog stuba na svakom ekranu.
- **Mali autorski trag**: npr. diskretan potpis u footeru ili easter egg (praznik, prazan dan, ponoć) koji pokazuje da je iza aplikacije osoba.

Pravilo: **hrabrost troši na jednom mjestu.** Jedan element je upečatljiv, sve oko njega je mirno i disciplinovano.

## 5. Šta izbjegavati (znakovi AI dizajna)

Ovo su stvari koje odmah otkrivaju generisan dizajn. Ne koristi ih osim ako ih ja izričito tražim:

- krem pozadina (oko #F4F1EA) sa serifnim naslovom i terakota akcentom (oko #D97757)
- skoro crna pozadina s jednim jarkim neon zelenim ili narandžastim akcentom
- sve izrezano u identične zaobljene kartice s istim sivim sjenama i gradijentima kao ukrasom
- isti border-radius na svemu, bez obzira na hijerarhiju
- MALI RAZMAKNUTI CAPS LABELI iznad svakog naslova
- jedna riječ u naslovu istaknuta drugom bojom ili kurzivom
- stringovi tipa "Brzo · Jednostavno · Pouzdano"
- strelica "→" na kraju svakog dugmeta i linka
- monospace font za sitne labele bez razloga
- numeracija 01 / 02 / 03 kad sadržaj nije stvarno niz koraka (proces rezervacije jeste niz, pa tu ima smisla)
- fade-in i slide-up na svakoj sekciji, hover animacija na svakoj kartici
- glassmorphism, blur blobovi u pozadini, generičke 3D ilustracije
- Inter, Roboto, Poppins ili Montserrat kao "default" izbor. Font biraj namjerno i objasni zašto

## 6. Interaktivnost i animacije

- Animacija mora odgovarati na nešto što korisnik uradi: odabere uslugu, prevuče dan, uzme termin, potvrdi. Treba pokazati šta se promijenilo.
- Dozvoljen je **jedan** orkestriran momenat koji nije pokrenut od korisnika, npr. ulazak na početni ekran ili otkrivanje potvrde. Samo jedan.
- Zauzet, slobodan i odabran termin moraju se razlikovati i bez boje (oblik, ikona, tekst), zbog pristupačnosti.
- Optimistic UI gdje ima smisla: termin se odmah prikaže kao uzet, pa se vrati ako API odbije, s jasnom porukom.
- Mikrointerakcije na formama: validacija dok kucaš, ali tek nakon što korisnik napusti polje.
- Poštuj `prefers-reduced-motion`: sve animacije imaju mirnu verziju.
- Animacije neka budu brze (150 do 300 ms za odgovore na akciju) i neka koriste `transform` i `opacity`, ne layout svojstva.

## 7. Tekst u aplikaciji

- UI je na bosanskom, prirodnim jezikom, ne prevodom s engleskog.
- Dugmad govore šta će se desiti: "Rezerviši termin", ne "Pošalji". Ista akcija ima isto ime kroz cijeli tok: dugme "Rezerviši termin" daje poruku "Termin rezervisan".
- Greške kažu šta se desilo i kako to popraviti, bez izvinjavanja i bez "Nešto je pošlo po zlu". Primjer: "Ovaj termin je u međuvremenu zauzet. Izaberi neki od slobodnih ispod."
- Prazno stanje je poziv na akciju, ne tužna ilustracija. Primjer: "Za ovaj dan nema slobodnih termina. Najbliži slobodan je u četvrtak u 10:30."
- Bez marketinških fraza ("revolucionarno", "bez muke", "u par klikova").

## 8. Tehnički minimum (ne pregovara se)

- Mobile first. Većina ljudi rezerviše s mobitela, pa ekran za izbor termina mora biti najbolji upravo na 390px.
- Vidljiv fokus za tastaturu na svim interaktivnim elementima; cijeli tok rezervacije mora proći samo tastaturom.
- Kontrast teksta najmanje WCAG AA.
- Dizajn tokeni (boje, razmaci, radijusi, fontovi, trajanja animacija) na jednom mjestu, kao CSS varijable ili u Tailwind configu. Bez hardkodiranih hex vrijednosti po komponentama.
- Fontovi lokalno (`@fontsource` ili fajlovi u projektu), s `font-display: swap`.
- Paziti na specifičnost CSS-a da se klase ne poništavaju međusobno.
- Svako stanje dizajnirano: učitavanje (skeleton koji liči na stvarni sadržaj), prazno, greška, uspjeh.
- Ne ruši performanse: bez teških 3D biblioteka, provjeri bundle size prije i poslije.

## 9. Redoslijed rada

1. Dizajn tokeni i osnovne komponente (dugme, input, termin, karta usluge).
2. Glavni tok rezervacije: izbor usluge → izbor dana i termina → podaci → potvrda. Ovdje ide potpisni element.
3. Početni ekran.
4. Ostali ekrani (login, moje rezervacije, admin/dashboard ako postoji).
5. Završno poliranje i provjera.

Nakon svakog koraka:
- napravi screenshote (desktop i mobitel) i pogledaj ih kritički prije nego mi javiš
- prije nego kažeš da je gotovo, "skini jedan aksesoar": ukloni bar jednu dekoraciju koja ne služi ničemu
- napravi zaseban commit s jasnom porukom (npr. `redesign: booking flow time strip`)
- kratko mi napiši šta si uradio i pokaži prije/poslije

## 10. Kad je gotovo

Redizajn je gotov kad:
- sve postojeće funkcije rade kao prije (prođi cijeli tok rezervacije ručno ili Playwright testom)
- nema grešaka u konzoli, a `npm run build` prolazi bez upozorenja vezanih za izmjene
- aplikacija izgleda dobro na 390px, 768px i 1440px
- neko ko je vidi prvi put može reći po čemu je drugačija od drugih booking aplikacija
- napisao si kratak `DESIGN.md` s tokenima, fontovima i pravilima, da budući rad ostane dosljedan

Kreni od koraka 0 i javi mi se s inventarom i screenshotima.

---

## 11. Bilješke za korak 1 (25.11.2026)

Ovo je materijal iz kojeg nastaju smjerovi u koraku 1. Konačan izbor palete i smjera je moj, poslije prijedloga. Ništa odavde još nije u kodu.

### Usklađeno s CLAUDE.md

- Commit poruke ostaju po pravilu iz CLAUDE.md: bosanski, jedna linija, bez prefiksa. Umjesto `redesign: booking flow time strip` ide npr. "Redizajn: traka vremena u kalendaru".
- Umjesto novog `DESIGN.md` se prepisuje postojeći `docs/DIZAJN.md`, da pravila dizajna ostanu na jednom mjestu.
- Trenutna podloga `#F4F1EA` je tačno ona krem iz sekcije 5. Novi smjer je ne zadržava.

### Palete s Color Hunta

Sajt palete crta JavaScriptom, pa sam ih čitao kroz Edge (Playwright), iz kategorija popular, vintage, retro, dark, earth, warm i cold. Kontrast je WCAG, isti račun kao u `docs/DIZAJN.md`. AA za tekst je 4,5, a za ivice i oblike 3.

**A. Čelik i žar (Zenica).** [37353e 44444e 715a5a d3dad9](https://colorhunt.co/palette/37353e44444e715a5ad3dad9) i žar `#EC5B38` iz [524646 a8a492 fcf2e5 ec5b38](https://colorhunt.co/palette/524646a8a492fcf2e5ec5b38).
Grafit na čeličnoj sivoj 8,50. Oksid `#715A5A` na sivoj 4,47 (malo ispod AA, za tekst se zatamni). Žar kao tekst na sivoj 2,42 ne prolazi, ali kao podloga s tamnim tekstom (`#16191D`) daje 5,14. Znači: žar je samo površina odabranog termina, nikad tekst.

**B. Tramvajska karta.** [efe8dd 76abae 303841 ff5722](https://colorhunt.co/palette/efe8dd76abae303841ff5722).
Grafit na papiru 9,76. Tirkiz `#76ABAE` na papiru 2,10, na grafitu 4,64. Narandžasta kao podloga s tamnim tekstom 5,57. Rizik: `#EFE8DD` je blizu zabranjene krem, pa bi papir trebao ići hladnije.
Tamnija varijanta: [0f3040 464858 a56f63 d99b7f](https://colorhunt.co/palette/0f3040464858a56f63d99b7f), breskva na tamnoplavoj 5,90.

**C. Ćilim.** [2d3c59 94a378 e5ba41 d1855c](https://colorhunt.co/palette/2d3c5994a378e5ba41d1855c): indigo, žalfija, šafran, broć, boje biljnih boja na ćilimu.
Indigo na bijeloj 11,04. Šafran s indigo tekstom 6,01, žalfija 4,08, broć 3,80 (oba samo za oblike i velike površine). Najviše karaktera od tri, i najveći rizik da ispadne "etno tematski park". Šara bi smjela biti samo na jednom mjestu.

Tamna, krvavocrvena opcija [6d0808 2d0000 757d6f eeead7](https://colorhunt.co/palette/6d08082d0000757d6feeead7) ima jak kontrast (10,30), ali previše liči na vino i luksuz, pa je odbacujem.

### Reference s Dribbblea

Dribbble je automatskom fetchu vraćao prazno, pa sam i njega gledao kroz Edge (tag modern-website, pretrage booking app i booking calendar).

Vrijedi uzeti:
- [Train Ticket Booking App UI (Nixtio)](https://dribbble.com/shots/26842001-Train-Ticket-Booking-App-UI): potvrda kao karta, s perforacijom na rubovima, linijom puta od-do i kodom na dnu. Kod nas QR za dolazak već postoji (Faza 10), pa "potvrda kao listić" iz sekcije 4 ima pravi sadržaj, nije ukras.
- [Recording Studio Booking Calendar (Emily Berg)](https://dribbble.com/shots/22024681-Recording-Studio-Booking-Calendar-EngineEars): resursi kao redovi, vrijeme vodoravno, crvena linija "sada" preko svih. Dobro za raspored providera i za "traku vremena" iz sekcije 4.
- Mreža termina na tamnom s cijenom u ćeliji (pretraga booking calendar, PROTOTYP): termin kao ćelija tabele s cijenom, ne kao dugme. Kod nas cijena postoji od Faze 11.

Na Dribbbleu se ponavlja i ono što ne uzimamo: fotografija preko cijelog hero dijela s naslovom od tri riječi, mockupi telefona u ruci, bijele kartice s plavim akcentom u svakom kalendaru, glassmorphism i 3D objekti, tamna pozadina s plavo-ljubičastim sjajem. Većina tih shotova je reklama za studio, ne aplikacija koja radi s pravim podacima.

### Izbor (25.11.2026)

Paleta je **A, Čelik i žar**, a potpisni element **potvrda kao karta za voz**. Između A i C sam pustio mentora da bira. Razlog: Zenica je i čelik i željeznički čvor, pa paleta i karta pričaju istu priču, a ćilim i karta bi bile dvije. A ima jednu jaku boju, koja znači samo "moje", isto pravilo koje je žuta imala do sada. C ima četiri zasićene boje koje bi se otimale oko stanja termina. Rizik A: siva može ispasti tmurno, a žar kao tekst ne prolazi kontrast, pa je uvijek samo površina.

Stanje prije je zapisano u `docs/redizajn/inventar-prije.md`, a screenshotovi su u `docs/redizajn/prije/`.
