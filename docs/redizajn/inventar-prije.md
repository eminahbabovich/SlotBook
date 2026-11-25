# Redizajn: stanje prije (25.11.2026)

Korak 0 iz `REDESIGN.md`. Screenshotovi su u `prije/`, svaki ekran na 1440 i 390 px, s pravim backendom i probnim podacima (Čitaonica PMF, tri resursa, rezervacije u svim stanjima, admin i klijent). Ime fajla je redni broj i ekran, npr. `12-kalendar-sto-mobitel.png`. Pravi ih `frontend/e2e/screenshotovi.mjs` (25 ekrana × 2 širine), a ista skripta će praviti i slike poslije. Dolazak s QR kodom nije uslikan, jer traži kod iz pravog QR-a.

## Ekrani

Odjavljen korisnik: prijava, registracija, potvrda emaila, stranica koja ne postoji.

Klijent: organizacije, organizacija, kalendar resursa (sa izborom termina, ponavljanjem i listom čekanja), moje rezervacije (s QR kodom za dolazak i dugmetom Plati), nalog (kalendar na telefonu, izvoz podataka, brisanje naloga), prihvatanje pozivnice.

Admin i provider: organizacija s linkovima Raspored i Uredi, članovi i pozivnice, novi resurs, izmjena resursa, radno vrijeme i izuzeci, statistika (po stanju, popunjenost, CSV), raspored resursa po danu, dolazak s QR kodom.

Ukupno 17 ruta u `App.jsx`. Nema početne stranice za odjavljene: `/` vodi na prijavu.

## Kako je stil napravljen

Običan CSS, bez Tailwinda i bez UI biblioteke. Tokeni su u `tokeni.css` (39 linija), globalne klase u `index.css`, a svaka funkcionalnost ima svoj CSS (`kalendar.css`, `rezervacije.css`...). Ukupno 10 fajlova i 737 linija. Nijedna hex boja nije van `tokeni.css`. Fontovi su Big Shoulders Display, IBM Plex Sans i IBM Plex Mono preko Fontsourcea.

Zajedničko: `.dugme` i `.dugme-tekst`, `Polje` za sva polja forme, `.tabla` za liste (organizacije, resursi, članovi), `NijeUcitano` za grešku s dugmetom za ponovni pokušaj, `Zaglavlje`. Modala nema, potvrde su u samoj stranici.

## Stanja

Učitavanje ima skeleton samo u kalendaru, prazne linije umjesto termina. Na ostalim ekranima je tekst "Učitava se..." (24 mjesta). Greška je svuda ista komponenta (19 fajlova): crvena linija, rečenica i dugme. Prazno stanje je jedna rečenica, npr. "Za ovaj dan nema rezervacija." ili "Ova organizacija još nema ništa za rezervaciju." Neke su već poziv na akciju (prvi slobodan termin u kalendaru), druge nisu.

## Brojke prije

`npm run build`: JS 417,12 kB (126,72 kB gzip), CSS 15,16 kB (3,24 kB gzip), fontovi 6 woff2 fajlova od 28 do 46 kB. U konzoli: 401 na prvom učitavanju kad korisnik nije prijavljen (pokušaj osvježavanja sesije bez kolačića, očekivano), i 404 za `favicon.ico`, jer aplikacija nema ikonu.

## Šta je najslabije

Cijela aplikacija: podloga `#F4F1EA` je ona krem iz liste znakova AI dizajna. Linkovi su skoro sve: navigacija, "Raspored", "Uredi", "Otkaži", pa se ne vidi šta je glavna akcija. Na desktopu je sadržaj na lijevoj trećini, a ostatak ekrana je prazan papir. Nema ikone, nema logotipa osim riječi SLOTBOOK.

Prijava i registracija: forma u gornjem lijevom uglu i ništa drugo. Prvi ekran koji neko vidi ne kaže šta je SlotBook.

Organizacije: lista od jednog reda. Nema naznake šta se dešava danas ni sljedeće rezervacije.

Kalendar: najbolji ekran i jedini s karakterom (tabla, žuta za moje, šrafura za zauzeto, monospace vremena). Slabo: dani su sedam jednakih kutija, a izabrani termin otvara obrazac ispod reda, koji na mobitelu gura ostatak liste. Cijena se ne vidi u redu termina.

Moje rezervacije: kartica je niz od pet redova teksta iste težine. Stanje ("potvrđena", "čeka uplatu") je sitan sivi tekst, a najvažnija je informacija. QR kod se otvara usred liste. Ovdje ide karta za voz.

Raspored: lista bez vremenske ose, pa se ne vidi gdje su rupe u danu. Otkazana rezervacija se vidi samo po precrtanom vremenu.

Forme (novi resurs, izmjena, članovi): uska kolona na širokom ekranu, sve napomene iste veličine kao oznake. Radno vrijeme pokazuje `08:00 AM` jer `input type=time` prati jezik browsera, ne zonu organizacije.

Statistika: trake su crne i debele, sve iste boje, pa "otkazana" i "potvrđena" izgledaju jednako važno.

Prazno stanje i greška: rečenica bez ikakvog oblika. Na desktopu se gube u praznom ekranu.
