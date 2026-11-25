# Redizajn: stanje poslije (25.11.2026)

Isti ekrani i isti podaci kao u `inventar-prije.md`, uslikani istom skriptom (`frontend/e2e/screenshotovi.mjs`) na 1440 i 390 px. Slike su u `poslije/`, s istim imenima kao u `prije/`, pa se mogu porediti jedna do druge. Ovaj put je skripta išla na backend s praznom e2e bazom (port 5434) i Mailpitom iz `frontend/e2e/compose.yml`, a ne na razvojnu bazu.

Smjer je "Čelik i žar" iz `smjer.md`: hladna siva podloga kao lim, grafitni tekst, a jedina topla boja je žar, i ona uvijek znači "tvoje". Pravila i vrijednosti su u `docs/DIZAJN.md`.

## Šta se promijenilo

Kalendar: dani su traka kao lenjir, a ne sedam kutija, i dan s mojim terminom ima tačkicu od žara. Termin je limena ploča, slobodan ima prazan krug, izabran se izdigne i krug se popuni, moj je pun žar s ikonom karte, a zauzet je šrafura bez ploče. Cijena plaćenog termina se vidi u redu prije klika. Potvrda više nije obrazac ubačen u listu, nego karta desno od liste, a na telefonu karta prikovana za dno. Poslije rezervacije karta izađe iz automata s brojem rezervacije i žigom.

Moje rezervacije: svaka rezervacija je karta za voz s putem od-do, brojem iz ID-ja i žigom stanja na kuponu. Potvrđena ima žig od žara, ona na čekanju isprekidanu ivicu, otkazana precrtano vrijeme, a završena rupu kroz kupon.

Prijava je sad početni ekran: jedna rečenica šta je SlotBook i primjer karte za sutra. Ranije je bila forma u gornjem lijevom uglu i ništa više.

Ostali ekrani nisu mijenjani pojedinačno, ali su dobili nove tokene (boje, Archivo, dugme kao ploča, polje na limu) i ista stanja: učitavanje je kostur od ploča umjesto teksta "Učitava se..." na 20 mjesta, greška je ploča s trakom i ikonom, a prazno stanje isprekidan obris. Statistika boji trake po stanju kao kalendar. "Obriši nalog" i "Otkaži rezervaciju" su u boji greške. Aplikacija ima ikonu u tabu, pa nema više 404 za `favicon.ico`.

## Brojke

`npm run build` prije i poslije:

| | prije | poslije |
|---|---|---|
| JS | 417,12 kB (126,72 kB gzip) | 420,07 kB (127,98 kB gzip) |
| CSS | 15,16 kB (3,24 kB gzip) | 22,34 kB (5,01 kB gzip) |
| fontovi | Big Shoulders, Plex Sans, Plex Mono, 6 woff2 od 28 do 46 kB | Archivo s osom širine (latin 90 kB, latin-ext 86 kB) i Plex Mono (latin 15 kB, latin-ext 13 kB) |

Najveći trošak su fontovi: Archivo s osom širine je teži od Big Shouldersa, ali jedan fajl daje i široke naslove i normalan tekst. Browser skida samo podskupove koje stranica koristi (`unicode-range`), pa vijetnamski i ćirilica ne idu korisniku.

Testovi: 256 Vitest testova i 8 Playwright testova prolaze. Promijenjeni su testovi koji su tražili stari tekst ili oblik ("MOJE" je sad "tvoje", "Rezervacija se šalje" je "Rezervišem...", red u Mojim rezervacijama je karta). Novi testovi dokazuju kartu poslije rezervacije, broj karte iz ID-ja, cijenu u redu, tačkicu na danu s terminom, primjer karte na prijavi i kostur učitavanja.

Provjereno u Edge-u (Playwright): 390, 768 i 1440 px, animacija karte traje 300 ms, a uz `prefers-reduced-motion` 0 ms. U konzoli ostaje samo 401 na prijavi, kad aplikacija pokuša osvježiti sesiju bez kolačića. To je očekivano i nije od redizajna.

## Greška koju je redizajn otkrio

Poslije rezervacije fokus je trebao preći na poruku "Termin je tvoj...", ali je padao na `body`. Poruka je imala `:empty { display: none }`, a `focus()` se zove prije nego React upiše tekst, kad je element još sakriven. Vitest to nije vidio, jer jsdom ne primjenjuje CSS. Našao sam je tek kad sam u Edge-u pitao `document.activeElement`. Ista klasa je i u Rasporedu i Mojim rezervacijama, pa je popravka i tamo.

## Šta nije urađeno

Polja za vrijeme (`input type=time`) i dalje pokazuju AM/PM kad je browser na engleskom. Žig "otkazana" se ne utiskuje animacijom, jer bi se utiskivao pri svakom otvaranju liste. Forme za admina (novi resurs, radno vrijeme, članovi) imaju nove tokene, ali raspored im je isti kao prije: uska kolona na širokom ekranu. Na desktopu je dosta ekrana i dalje u lijevoj polovini. Ništa od ovoga nije provjereno na pravom telefonu, samo u Edge-u s veličinom telefona, i nije provjereno čitačem ekrana, samo testovima po ulozi i imenu.
