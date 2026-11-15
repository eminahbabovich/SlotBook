# Dizajn

**Izbor: još nije napravljen.** Ispod su tri smjera predložena 15.11.2026. Kad izaberem jedan, ostala dva se brišu iz ovog fajla, a vrijednosti izabranog idu u kod kao CSS varijable na jednom mjestu. Boja, font ili razmak kojeg ovdje nema ne ulazi u UI dok se prvo ne dopiše ovdje.

Sva tri smjera polaze od domena (termin, red, raspored, čitaonica), a ne od SaaS templatea. Nema ljubičasto-plavih gradijenata, istih bijelih kartica sa sjenom, emojija umjesto ikona ni centriranog heroja s dva dugmeta. Nijedan glavni font nije `Inter`, `Roboto`, `Arial`, `Poppins` ni `system-ui`.

Fontovi: za svaki sam preko Fontsource API-ja (`api.fontsource.org/v1/fonts/<id>`) provjerio da ima subset `latin-ext`, koji pokriva č, ć, š, đ i ž. Svi se mogu self-hostati preko Fontsource paketa. Kako "Čitaonica PMF, đački termin, ž" stvarno izgleda u browseru nisam provjerio. To radim prije konačnog izbora.

Kontrast je računat po WCAG formuli za relativnu luminansu. Svaki par u tabelama prolazi AA za običan tekst (najmanje 4,5:1).

## Smjer 1: Red vožnje

Inspiracija je tabla s polascima na stanici i tramvajski red vožnje na stajalištu. Dan je tabla, a termin je jedan red u njoj: vrijeme lijevo, tabularnim ciframa, poravnato kao polasci. Ima jakih horizontalnih linija, a sjena i zaobljenih kartica gotovo nema. Žuta je jedina jaka boja i znači "ovo je tvoje", kao istaknut polazak.

Fontovi: `Big Shoulders Display` za naslove (uzak, kao natpisi na stanici), `IBM Plex Sans` za tekst i `IBM Plex Mono` za vremena i brojeve.

| Uloga | Hex | Kontrast |
|---|---|---|
| podloga (papir reda vožnje) | `#F4F1EA` | |
| tekst | `#16191D` | 15,63 na podlozi |
| sporedni tekst | `#5A6069` | 5,62 na podlozi |
| akcenat (signalna žuta) | `#F2B705` | tekst na žutoj 9,70 |
| greška | `#B3261E` | 5,79 na podlozi |

Razmaci su na mreži od 4 px, a red termina je visok 48 px. Zaobljenje je 2 px, ili nikakvo.

Stanja termina:
- **slobodno:** red s tankom ivicom i vremenom, tekst "slobodno";
- **zauzeto:** siva kosa šrafura preko reda, precrtano vrijeme, tekst "zauzeto";
- **moja rezervacija:** puna žuta traka s tamnim tekstom i oznakom "MOJE";
- **moja na čekanju:** žuta isprekidana ivica bez punjenja i oznaka "ČEKA POTVRDU".

Ton je kratak i informativan, kao najava na stanici: "Termin 08:00 je tvoj. Potvrda stiže mailom."

Na mobilnom je jedan dan kao lista polazaka, što je za ovaj smjer prirodno.

## Smjer 2: Katalog čitaonice

Inspiracija su kartice iz kataloga biblioteke i pečat "posuđeno". Rezervacija izgleda kao kataloška kartica: naslov resursa serifom, a vrijeme i broj kao signatura, pisaćom mašinom. Papir se dočarava tipografijom i tankim linijama, ne teksturom ni slikom. Bordo crvena je boja pečata.

Fontovi: `Newsreader` za naslove, `Public Sans` za tekst i `Courier Prime` za vremena i signature.

| Uloga | Hex | Kontrast |
|---|---|---|
| podloga (karton) | `#F6F0E4` | |
| tekst (tinta) | `#2A2118` | 13,92 na podlozi |
| sporedni tekst | `#6B5B45` | 5,77 na podlozi |
| akcenat (pečat) | `#8C2F2B` | 7,24, i obrnuto na pečatu |
| linkovi (plava tinta) | `#1F3A5F` | 10,12 na podlozi |

Razmaci su na mreži od 8 px. Kartice imaju zaobljenje 4 px i tanku ivicu, bez sjene.

Stanja termina:
- **slobodno:** prazna kartica s linijama za upis;
- **zauzeto:** kartica s otiskom pečata "ZAUZETO", koso, u sivoj;
- **moja rezervacija:** bordo pečat "MOJE" i moje ime upisano na kartici;
- **na čekanju:** pečat isprekidanom linijom "ČEKA".

Ton je smiren, kao bibliotekarka koja zna gdje je sve: "Sto br. 3 je tvoj u utorak od 08:00. Otkazati možeš do ponedjeljka."

Na mobilnom kartice idu jedna ispod druge. Rizik je da kod sedmičnog prikaza za providera kartice postanu pretijesne.

## Smjer 3: Šalterski listić

Inspiracija je listić s brojem iz aparata na šalteru (pošta, općina, banka) i termalna priznanica. Potvrda rezervacije je otkinut listić s perforacijom, brojem i vremenom monospaceom. `.ics` je "kopija za klijenta". Pozadina je siva šaltera, a listići su topla bijela.

Fontovi: `Archivo` za naslove i tekst (u užoj i široj širini) i `Space Mono` za broj, vrijeme i kodove.

| Uloga | Hex | Kontrast |
|---|---|---|
| pozadina (šalter) | `#E4E8E3` | |
| listić | `#FFFDF7` | |
| tekst | `#1B1B1B` | 16,93 na listiću, 13,90 na šalteru |
| sporedni tekst | `#5C625E` | 6,14 na listiću |
| akcenat (termalna narandžasta) | `#C2410C` | 5,09, i obrnuto |
| potvrđeno | `#2B7A4B` | 5,18 na listiću |

Razmaci su na mreži od 4 px. Listić ima ravne ivice, a perforacija je nazubljena linija (SVG), ne zaobljen ugao.

Stanja termina:
- **slobodno:** vrijeme u okviru s isprekidanom ivicom, kao mjesto za otkidanje;
- **zauzeto:** sivo, precrtano, s ikonom katanca i tekstom "zauzeto";
- **moja rezervacija:** puni listić s brojem rezervacije i zelenom kvačicom;
- **na čekanju:** listić s narandžastom trakom "ČEKA POTVRDU".

Ton je kratak, kao ekran iznad šaltera: "Tvoj broj: 042. Termin: utorak 08:00."

Na mobilnom je jedan dan kao niz listića. Rizik je da monospace na velikim površinama postane naporan za čitanje, pa ga treba držati samo za brojeve.

## Prijedlog

Predlažem smjer 1, red vožnje. Kalendar termina je već po prirodi red vožnje (vremena u kolonama, redovi), pa smjer pomaže čitljivosti, a ne samo izgledu. Tabularne cifre poravnavaju vremena, i to baš na dan kad se sat pomjera. Mobilni prikaz je prirodno lista. Tri stanja se razlikuju i bez boje: ivica, šrafura i puna traka s tekstom. Cijena: uski display font treba paziti na malim ekranima, a na naslove ga ne treba stavljati preveliko.
