# Redizajn 2: smjer Mjed i emajl (25.11.2026)

Prvi redizajn ("Čelik i žar") je bio previše ravan i bezličan: jedna siva površina, zaobljenje od 3 px na svemu, jedan font, jedna boja. Ovaj put sam smjer izabrao sam, po smjernicama iz moje poruke od 25.11. (reference: Airbnb 2025, Amie, Family, landonorris.com, Linear). Ovaj fajl je za to da se smjer ne raspline usput.

## Ideja

Kazandžiluk, ulica kazandžija na Baščaršiji: mjed koja se kuje čekićem, i emajl kojim se boje ploče i značke. Termin je emajlirani žeton koji uzmeš s ploče, a rezervacija je karta s mjedenom folijom koju nosiš u džepu. Sve što se može dodirnuti ima debljinu: žeton se pritisne, karta se nagne prema prstu, ploče leže jedna na drugoj. Mjed znači "tvoje", a četiri emajl boje razlikuju resurse, kao šareni blokovi u Amie kalendaru.

Lokalno je u materijalu, a ne u ukrasu: nema minareta, sebilja ni ćilima na ekranu. Vidi se u imenima (Čitaonica PMF, Teretana Ilidža), u zoni `Europe/Sarajevo`, i na jednom mjestu u priči o vremenu: Sahat-kula na Baščaršiji i danas pokazuje vrijeme po zalasku sunca.

## Paleta

Dvije teme, svaka dizajnirana posebno. Dan je hladan porculan, ne papir i ne krem. Noć je tamna tinta nad čaršijom, a ne crna, i u njoj mjed najviše sija.

| Ime | Dan | Noć | Uloga |
|---|---|---|---|
| podloga | `#E7EAF1` porculan | `#121735` tinta | pozadina |
| površina | `#F6F7FA` | `#1A2045` | ploče, polja |
| izdignuto | `#FFFFFF` | `#232A56` | žeton, karta, meni |
| tekst | `#151933` tinta | `#ECEEF8` | sav tekst |
| sporedni | `#4B5172` | `#A4AACB` | napomene |
| mjed | `#C9A04E` | `#D9B566` | moje, folija, glavno dugme |

Emajl za resurse: šipak `#E5484D`, šafran `#F2B530`, tirkiz `#12A594`, lapis `#3E6AE6`. Emajl je površina ili rub, nikad tekst na podlozi. Kontrasti su u `docs/DIZAJN.md`.

## Fontovi

- **Anybody** (variable, širina 50 do 150 %, težina 100 do 900): naslovi i velike cifre. Širina je potpis: na landingu se slova šire i skupljaju prema kursoru, u aplikaciji su naslovi široki i teški, a cifre vremena uske i visoke.
- **Hanken Grotesk** (variable): sav tekst, forme, dugmad. Miran, čitljiv, s tabularnim ciframa.
- **IBM Plex Mono**: samo na karti, jer je karta odštampana.

Sva tri imaju latin-ext (č, ć, š, đ, ž), provjereno u `files/` paketa.

## Potpisni elementi

1. **Žeton.** Slobodan termin je emajlirani žeton s debljinom (svijetao gornji rub, sjena ispod). Na pritisak se spusti, uzet termin "odleti" s ploče u kartu. Zauzet je udubljen i šrafiran, bez debljine. Moj je mjeden. Razlikuju se i bez boje: debljina, šrafura, ikona i tekst.
2. **Karta s folijom.** Karta iz prvog redizajna ostaje, ali bogatija: mjedena folija s prelivom koji prati nagib, 3D nagib prema kursoru ili prstu (na telefonu na dodir, bez dozvole za žiroskop), QR za dolazak i dugme "Sačuvaj kao sliku".
3. **Slojevi.** Tri nivoa dubine (podloga, ploča, izdignuto) i različita zaobljenja po nivou, umjesto jednog radijusa svuda: polje 10 px, žeton 14 px, karta 20 px, ploča 28 px.

## Tri spektakularna momenta

1. **Hero na landingu.** Ogroman naslov u Anybody, čija slova mijenjaju širinu i težinu prema kursoru (na telefonu prema skrolu), i pored njega pravi mini demo: ploča sa žetonima za sutra u čitaonici, klik uzme termin i odštampa kartu. To je prve tri sekunde, i to je ono što aplikacija stvarno radi.
2. **Uzimanje termina.** Žeton se odvoji od ploče i preleti u kartu, karta se odštampa, a folija jednom prođe svjetlom. Ovo je trenutak zbog kojeg se aplikacija pamti, pa dobija najviše pažnje. Na landingu let radi motion (`layoutId`), a u kalendaru View Transition (`tema/prelaz.js`): motion bi u glavni bundle donio oko 50 kB, a View Transition ne košta ništa. Kalendar je s njim narastao 2 kB. Browser bez View Transitions samo promijeni stanje, bez leta.
3. **Prelazi između ekrana.** View Transitions: naziv resursa i njegova emajl boja prelaze s liste na kalendar, karta iz Mojih rezervacija se otvori iz liste. Gdje browser to ne zna, ekran se samo promijeni. Urađeno je pola (25.11.2026): naziv organizacije i resursa preleti s pločice u naslov, ali emajl pločica ne preleti (na listi je `::before`, a pseudo-element ne može dobiti ime prelaza iz `style`), a karta se iz liste ne otvara, jer Moje rezervacije nemaju ekran jedne karte.

Sve ostalo je mirno. Hover samo gdje nešto znači (žeton, dugme, karta). `prefers-reduced-motion` gasi letove, nagib i prelaze, a stanja ostaju ista.

## Landing (prvi ekran za odjavljene)

Hero s demom, pa tri teška problema kao sekcije koje se mogu probati, pa ulaz u demo:

- **Dvadeset ruku, jedan termin.** Dugme pošalje 20 žetona na isti termin, jedan se zaključa, 19 se odbije sa `409`. Ispod: šta to čuva (`EXCLUDE` u bazi) i koji test to dokazuje.
- **Sat koji preskoči.** Noć 28. marta 2027, kad sat sa 02:00 skoči na 03:00. Klizač kroz noć, termin od 02:00 ne postoji, a termin u 08:00 ostaje u 08:00. Ovdje je Sahat-kula.
- **Tuđa vrata.** Čitaonica i teretana kao dvije ploče. Admin teretane pokuša 33 zahtjeva u čitaonicu, svi se vrate sa `403`, a u bazi se ništa ne promijeni.
- **Uđi kao Hana**, Mirza ili Amila jednim klikom.

Pravi 3D (WebGL) ne koristim. Odlučio sam da je jedini 3D karta, napravljena CSS transformacijama: radi na svakom uređaju, nema three.js u bundleu, a izgleda bolje od polovičnog 3D objekta. Budžet za JS: aplikacija iza prijave najviše +80 kB. Landing je poseban chunk koji se učitava samo za odjavljene. Poslije landinga (25.11.2026): glavni bundle 426 kB (prije 421), landing 152 kB (51 kB gzip, najviše motion) i lenis 19 kB. To je više od 120 kB koje sam planirao za landing, pa je kandidat za `LazyMotion` kad bude jasno koje funkcije motion aplikacija stvarno koristi.

## Šta ne radimo

Lista iz `REDESIGN.md` sekcija 5 i dalje važi. Posebno: nema ljubičasto-plavih gradijenata, glassmorphisma ni blur blobova; gradijent je dozvoljen samo na mjedu i foliji, gdje glumi metal; sjene su slojevite i nose informaciju o nivou, nisu ista siva sjena ispod svega.
