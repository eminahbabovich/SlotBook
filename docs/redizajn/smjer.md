# Redizajn: smjer Čelik i žar (prijedlog, 25.11.2026)

Korak 1 iz `REDESIGN.md`. Ovo je prijedlog, ne odluka. Kad ga odobrim, vrijednosti idu u `docs/DIZAJN.md` i `tokeni.css`.

## Ideja

Zenica je grad čelika i željeznički čvor: sivi lim, utisnuta slova na natpisnim pločicama mašina i vagona, i kartonska karta za voz koju kondukter probuši. SlotBook preuzima tri stvari. Podloga je hladan čelik, a ne papir. Naslovi su široka, teška slova, kao utisnuta u lim. Rezervacija je karta. Jedina topla boja je žar, i znači samo jedno: ovo je tvoje.

## Boje

Paleta [37353e 44444e 715a5a d3dad9](https://colorhunt.co/palette/37353e44444e715a5ad3dad9) i žar `#EC5B38` s Color Hunta. Lim i ugalj su izvedeni iz nje, a oksid je zatamnjen da prođe AA.

| Token | Hex | Uloga | Kontrast |
|---|---|---|---|
| `--celik` | `#D3DAD9` | podloga svega | |
| `--lim` | `#E9EDEC` | površine: polja, red termina, karta | |
| `--grafit` | `#37353E` | tekst, linije, glavno dugme | 8,50 na čeliku, 10,22 na limu |
| `--ugalj` | `#1F1E24` | tekst na žaru, pritisnuto dugme | 4,82 na žaru |
| `--oksid` | `#5E4A4A` | sporedni tekst, napomene | 5,79 na čeliku, 6,96 na limu |
| `--zar` | `#EC5B38` | samo moja rezervacija, nikad tekst | 2,42 na čeliku, zato uvijek s ivicom od ugljena |
| `--greska` | `#9A2A22` | greška, uz ikonu | 5,42 na čeliku |

Žar ne ide na dugmad, linkove ni ukras. Glavno dugme je grafit. Greška i žar su bliske boje, pa se greška nikad ne prepoznaje samo po boji: uvijek ima ikonu prekinutog kruga i tekst.

## Tipografija

**Archivo** (Omnibus-Type, OFL, `@fontsource-variable/archivo`) za sve osim karte. Ima osu širine od 62 do 125, pa je jedna porodica i za naslove i za tekst: naslovi su široki i teški (`wdth 125`, `wght 800`), kao utisnuti u lim, a tekst je normalne širine. Provjereno u Edgeu 25.11.2026: svih deset slova č, ć, š, đ, ž crta sam font (canvas, isto mjerenje kao u Fazi 7), a uz `tabular-nums` su "11:11" i "00:00" iste širine (104 px), bez njega nisu (96 i 104). Zato vremena u kalendaru više ne trebaju monospace.

**IBM Plex Mono** ostaje samo na karti: broj karte i vremena polaska, kao otisak štampača na blagajni. Već je u projektu i provjeren za latin-ext. Big Shoulders i Plex Sans izlaze.

| Uloga | Veličina | Širina / težina | Prored |
|---|---|---|---|
| naslov prijave | 49 px (mobitel 39) | 125 / 800 | 1,0 |
| naslov stranice | 39 px (mobitel 31) | 125 / 800 | 1,05 |
| podnaslov | 25 px | 112 / 700 | 1,15 |
| vrijeme termina | 20 px | 100 / 600, tabularne | 1,2 |
| tekst | 16 px | 100 / 400 | 1,5 |
| oznake, napomene | 14 px | 100 / 500 | 1,45 |

Skala je 1,25. Ispod 14 px se ne ide. Bez verzala s razmakom.

## Oblici, razmaci, pokret

Zaobljenje ima hijerarhiju: redovi termina i trake su ravni (0), dugmad i polja 3 px, karta 12 px s polukružnim urezima na perforaciji. Razmaci: 4, 8, 12, 16, 24, 32, 48, 64. Sjena postoji samo na karti i na izdignutom terminu, kao utisnut lim: svijetla linija gore i tamna dole, ne siva mrlja.

Trajanja: 150 ms za odgovor na klik, 220 ms za promjenu stanja, 300 ms za kartu. Sve preko `transform` i `opacity`. Uz `prefers-reduced-motion` nema pomjeranja, samo promjena.

## Stanja termina

Svako stanje se razlikuje i bez boje (oblik i tekst):

| Stanje | Oblik | Tekst |
|---|---|---|
| slobodno | red na limu, prazan krug ○ desno | slobodno, cijena ako je ima |
| odabrano | red izdignut 2 px, lijeva ivica 4 px grafit, pun krug ● | odabrano |
| tvoje | puna površina žar, ivica od ugljena, ikona karte | tvoje |
| čeka potvrdu / čeka uplatu | isprekidana ivica žar, pola reda šrafirano | čeka potvrdu / plati do 12:03 |
| ponuđen tebi (lista čekanja) | puna ivica žar, ikona sata | ponuđen tebi do 14:20 |
| zauzeto | šrafura, precrtano vrijeme | zauzeto |

## Raspored

Kalendar, desktop 1440:

```
┌────────────────────────────────────────────────────────────────────────────┐
│ SLOTBOOK▮                                  Moje karte   Hana Šehić   Odjava│
╞════════════════════════════════════════════════════════════════════════════╡
│ Čitaonica PMF                                                              │
│ STO 4, TIHI DIO                               60 min · besplatno · 1 mjesto│
│ Četvrti sprat, bez razgovora.                                              │
│                                                                            │
│  ‹  sri 25 │ ČET 26 │ pet 27 │ sub 28 │ ned 29 │ pon 30 │ uto 1  ›          │
│ ─┴─────────┴━━━━━━━━┴────────┴────────┴────────┴────────┴───────┴──────    │
├───────────────────────────────────────────┬────────────────────────────────┤
│ 08:00–09:00                     slobodno ○│  ╭──────────────────────────╮  │
│▓09:00–10:00▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ tvoje ▣ ▓│  │ četvrtak, 26. novembar   │  │
│█10:00–11:00                     odabrano ●│  │ 10:00 ●━━━━━━━━━━● 11:00 │  │
│ 11:00–12:00 ╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱ zauzeto ╱│  │ Sto 4, tihi dio          │  │
│ 12:00–13:00 ╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱ zauzeto ╱│  │ Ponavljanje: samo jednom │  │
│ 13:00–14:00                     slobodno ○│  │ [ Rezerviši termin ]     │  │
│ ...                                       │  ╰──────────────────────────╯  │
└───────────────────────────────────────────┴────────────────────────────────┘
```

Dani su traka kao lenjir, s crtom ispod izabranog dana, a ne sedam jednakih kutija. Dan u kojem imam rezervaciju ima tačkicu od žara. Desno je karta u nastajanju: šta ću dobiti ako kliknem.

Kalendar, mobitel 390:

```
┌──────────────────────────────┐
│ SLOTBOOK▮              ☰     │
╞══════════════════════════════╡
│ Čitaonica PMF                │
│ STO 4, TIHI DIO              │
│ 60 min · besplatno           │
│ sri 25│ČET 26│pet 27│sub 2 → │  traka se pomjera prstom
│ ──────┴━━━━━━┴──────┴─────── │
│ 08:00–09:00       slobodno ○ │
│▓09:00–10:00▓▓▓▓▓▓▓▓ tvoje ▣ ▓│
│█10:00–11:00       odabrano ● │
│ 11:00–12:00 ╱╱╱╱╱╱ zauzeto ╱ │
│ ...                          │
├──────────────────────────────┤
│ ╭──────────────────────────╮ │  karta je prikovana za dno
│ │ 10:00 ●━━━━━━━━━● 11:00  │ │  i ne gura listu
│ │ [ Rezerviši termin ]     │ │
│ ╰──────────────────────────╯ │
└──────────────────────────────┘
```

Prijava postaje početni ekran, bez nove rute: lijevo jedna rečenica šta je SlotBook i primjer karte, desno forma. Na mobitelu je karta iznad forme, manja.

## Potpisni element: karta

```
╭──────────────────────────────────────┬─────────────╮
│ SLOTBOOK         KARTA № C16C-3710   ┊ ▓▓▓▓▓▓▓▓▓▓▓ │
│                                      ┊ ▓▓  QR   ▓▓ │
│ četvrtak, 26. novembar               ┊ ▓▓▓▓▓▓▓▓▓▓▓ │
│ 09:00 ●━━━━━━━━━━━━━━━━━━━━━● 10:00  ┊             │
│ Sto 4, tihi dio                      ┊  ╭───────╮  │
│ Čitaonica PMF · Europe/Sarajevo      ┊  │POTVRĐ.│  │  žig
╰──────────────────────────────────────┴──╰───────╯──╯
```

Moje rezervacije su karte, a ne kartice teksta. Lijevi dio je put od početka do kraja termina, desni je kupon s QR kodom za dolazak (već postoji od Faze 10), odvojen perforacijom. Broj karte je prvih osam znakova ID-ja rezervacije. Stanje je žig na kuponu: potvrđena, čeka potvrdu, čeka uplatu (uz dugme Plati). Otkazana karta je precrtana žigom "otkazana". Iskorištena (dolazak upisan) ima rupu od kondukterskih kliješta, krug izrezan kroz kartu. Rupa i žig se vide i bez boje.

Karta se ne crta kao slika. To je HTML s CSS-om, pa je čitljiva čitaču ekrana i tekst se može kopirati.

## Kako se ponaša

Odabir termina: red se izdigne za 2 px i dobije lijevu ivicu (150 ms), a karta desno (ili dole na mobitelu) upiše novo vrijeme. Pokazuje šta će se desiti prije klika.

Rezervacija: kad server potvrdi, karta "izađe iz automata", klizne iz proreza za 300 ms, a red u kalendaru postane žar. To je jedini orkestriran momenat, i pokreće ga korisnik.

Otkazivanje: žig "otkazana" se utisne (skala 1,1 na 1, 150 ms).

Prijava: primjer karte je na mjestu od početka, bez animacije ulaska.

Forme: greška se pokaže kad korisnik napusti polje, ne dok kuca.

## Gdje se ne slažem s REDESIGN.md

Optimistic UI za rezervaciju ne bih radio. Glavna tvrdnja projekta je da od 20 istih zahtjeva prođe tačno jedan. Optimistic prikaz bi kod ostalih 19 na sekundu napisao "tvoje" pa to povukao, a to je laž baš na mjestu gdje projekat dokazuje istinu. Umjesto toga dugme odmah kaže "Rezervišem..." i zaključa se, a odgovor stiže za manje od 105 ms (p95 iz k6 testa u Fazi 9, na laptopu, ne na Renderu). Optimistic ide tamo gdje je jeftino i povratno: "Javi mi ako se oslobodi", otvaranje QR koda.

## Samokritika

Šta je u prvoj verziji ličilo na bilo koju booking aplikaciju, i šta sam promijenio:

1. Tamna traka zaglavlja preko cijele širine, kao svaki SaaS. Sad je zaglavlje na čeliku, odvojeno dvostrukom linijom (tanka i debela), kao spoj dvije ploče lima.
2. Karta s QR-om je bila skoro ista kao shot s Dribbblea (Nixtio). Naše je ono što tamo nema: žig za stanje, rupa za iskorištenu, broj iz stvarnog ID-ja i put od-do koji je stvarno trajanje termina. Bar-kod kao ukras je izbačen.
3. Sedam jednakih kutija za dane je u svakom kalendaru na Dribbbleu. Sad je to traka kao lenjir.
4. Žar je bio i na glavnom dugmetu. Izbačen, jer bi onda značio i "klikni" i "tvoje".
5. Animacija ulaska karte na prijavi i animacija štampanja poslije rezervacije su bile dva orkestrirana momenta. Prijava je sad mirna.
6. "Skidam jedan aksesoar": bila je i tekstura brušenog metala na podlozi. Izbačena, jer je to tačno tematski park iz sekcije 4.

Rizik koji ostaje: siva podloga s grafitom može djelovati hladno i tmurno. Ako na prvom ekranu (tokeni i dugmad) tako izgleda, prvo se mijenja `--celik` prema toplijoj sivoj, a ne dodaje se boja.
