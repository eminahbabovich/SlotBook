// Sve računanje vremena za prikaz je ovdje. Dva pravila:
// - trenutak (Instant iz backenda, "2035-11-19T07:00:00Z") se u sat ili dan pretvara samo
//   uz zonu organizacije, nikad u zoni browsera;
// - dan je string "YYYY-MM-DD" (kao LocalDate u Javi), a ne Date, jer new Date('2035-11-19')
//   je ponoć u UTC-u, a to je u Americi još 18. novembar.

// en-CA formatira datum baš kao YYYY-MM-DD, pa nema ručnog slaganja dijelova.
const formatDana = new Map()
const formatSata = new Map()

function zaZonu(kes, zona, napravi) {
	if (!kes.has(zona)) kes.set(zona, napravi())
	return kes.get(zona)
}

export function danUZoni(trenutak, zona) {
	const f = zaZonu(formatDana, zona, () => new Intl.DateTimeFormat('en-CA', { timeZone: zona }))
	return f.format(new Date(trenutak))
}

export function satUZoni(trenutak, zona) {
	const f = zaZonu(
		formatSata,
		zona,
		// en-GB, a ne bs: sat je samo brojevi, a bosanski neki browseri nemaju (vidi nazivDana)
		() => new Intl.DateTimeFormat('en-GB', { timeZone: zona, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }),
	)
	return f.format(new Date(trenutak))
}

// Računa se u UTC-u, gdje dan uvijek ima 24 sata. Zato prelazak na ljetno vrijeme ne može
// preskočiti ni duplirati dan, a dan je samo kalendarski datum, bez sata.
export function dodajDane(dan, broj) {
	const [godina, mjesec, d] = dan.split('-').map(Number)
	return new Date(Date.UTC(godina, mjesec - 1, d + broj)).toISOString().slice(0, 10)
}

export function sedmicaOd(dan) {
	return Array.from({ length: 7 }, (_, i) => dodajDane(dan, i))
}

// Nazivi su ovdje, a ne iz Intl.DateTimeFormat('bs'): Edge 157 za 'bs' nema podataka i
// piše "M11 19, Mon", hr daje "studenoga", a sr-Latn "ponedeljak".
const DANI = ['nedjelja', 'ponedjeljak', 'utorak', 'srijeda', 'četvrtak', 'petak', 'subota']
const DANI_KRATKO = ['ned', 'pon', 'uto', 'sri', 'čet', 'pet', 'sub']
const MJESECI = ['januar', 'februar', 'mart', 'april', 'maj', 'juni', 'juli', 'august', 'septembar', 'oktobar', 'novembar', 'decembar']

// Dan je kalendarski datum, pa se dan u sedmici računa u UTC-u, bez zone browsera.
export function nazivDana(dan, duzina = 'dugo') {
	const datum = new Date(`${dan}T00:00:00Z`)
	const redni = datum.getUTCDate()
	if (duzina === 'kratko') return `${DANI_KRATKO[datum.getUTCDay()]} ${redni}.`
	return `${DANI[datum.getUTCDay()]}, ${redni}. ${MJESECI[datum.getUTCMonth()]}`
}

// Raspon sedmice iznad trake dana: "26. nov – 2. dec". Bez mjeseca u traci se "1." i "2." čine kao
// novembar, pa mjesec piše uz oba kraja, i kad je isti.
const MJESECI_KRATKO = ['jan', 'feb', 'mar', 'apr', 'maj', 'jun', 'jul', 'avg', 'sep', 'okt', 'nov', 'dec']

export function rasponDana(prvi, zadnji) {
	const kratko = (dan) => `${Number(dan.slice(8, 10))}. ${MJESECI_KRATKO[Number(dan.slice(5, 7)) - 1]}`
	return `${kratko(prvi)} – ${kratko(zadnji)}`
}

// Mjesec je "YYYY-MM", isto kao dan, bez Date objekta
export function mjesecUZoni(trenutak, zona) {
	return danUZoni(trenutak, zona).slice(0, 7)
}

export function dodajMjesece(mjesec, broj) {
	const [godina, m] = mjesec.split('-').map(Number)
	return new Date(Date.UTC(godina, m - 1 + broj, 1)).toISOString().slice(0, 7)
}

export function nazivMjeseca(mjesec) {
	const [godina, m] = mjesec.split('-').map(Number)
	return `${MJESECI[m - 1]} ${godina}`
}

// ISO broj dana kao java.time.DayOfWeek: 1 ponedjeljak ... 7 nedjelja, a niz DANI kreće od nedjelje
export function danUSedmici(iso) {
	return DANI[iso % 7]
}

// Oznaka zone ide samo kad bi sat na uređaju stvarno bio drugačiji. Ime zone nije dovoljno:
// Europe/Zagreb i Europe/Sarajevo su različita imena za ista vremena.
export function zonaUredjajaRazlicita(trenuci, zona, zonaUredjaja = Intl.DateTimeFormat().resolvedOptions().timeZone) {
	return trenuci.some((t) => satUZoni(t, zona) !== satUZoni(t, zonaUredjaja) || danUZoni(t, zona) !== danUZoni(t, zonaUredjaja))
}

// Koliko je zona u tom trenutku ispred UTC-a, u minutama (Sarajevo zimi 60, ljeti 120).
// Intl kaže koji je sat u zoni, a razlika tog sata i trenutka je pomak.
const formatDijelova = new Map()
function pomakMinuta(ms, zona) {
	const f = zaZonu(
		formatDijelova,
		zona,
		() => new Intl.DateTimeFormat('en-US', { timeZone: zona, hourCycle: 'h23', year: 'numeric', month: 'numeric', day: 'numeric', hour: 'numeric', minute: 'numeric' }),
	)
	const d = Object.fromEntries(f.formatToParts(new Date(ms)).map((dio) => [dio.type, dio.value]))
	return Math.round((Date.UTC(d.year, d.month - 1, d.day, d.hour, d.minute) - ms) / 60000)
}

function oznakaPomaka(minuta) {
	const apsolutno = Math.abs(minuta)
	const dvije = (broj) => String(broj).padStart(2, '0')
	return `${minuta < 0 ? '-' : '+'}${dvije(Math.floor(apsolutno / 60))}:${dvije(apsolutno % 60)}`
}

// Dan i sat kako ih korisnik upiše, u zoni organizacije, kao OffsetDateTime za backend:
// ('2026-12-24', '00:00') -> '2026-12-24T00:00:00+01:00'.
// Pomak se ne zna unaprijed, jer zavisi od trenutka koji tražimo. Zato se probaju pomaci dan
// prije i dan poslije (dva prelaska nikad nisu tako blizu) i zadrži onaj koji se poklapa s
// pomakom u dobijenom trenutku. Kad ne odgovara nijedan, taj sat tog dana ne postoji (02:30
// na dan prelaska na ljetno) i vraća se null. Kad odgovaraju oba, sat postoji dvaput (02:30
// na dan prelaska na zimsko) i uzima se raniji trenutak.
const DAN_MS = 24 * 60 * 60 * 1000
export function uTrenutakSPomakom(dan, sat, zona) {
	const [godina, mjesec, d] = dan.split('-').map(Number)
	const [h, min] = sat.split(':').map(Number)
	const kaoDaJeUtc = Date.UTC(godina, mjesec - 1, d, h, min)
	const ispravni = [pomakMinuta(kaoDaJeUtc - DAN_MS, zona), pomakMinuta(kaoDaJeUtc + DAN_MS, zona)]
		.map((pomak) => ({ pomak, trenutak: kaoDaJeUtc - pomak * 60000 }))
		.filter(({ pomak, trenutak }) => pomakMinuta(trenutak, zona) === pomak)
		.sort((a, b) => a.trenutak - b.trenutak)
	if (ispravni.length === 0) return null
	return `${dan}T${sat}:00${oznakaPomaka(ispravni[0].pomak)}`
}
