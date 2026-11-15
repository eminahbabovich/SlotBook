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

// Oznaka zone ide samo kad bi sat na uređaju stvarno bio drugačiji. Ime zone nije dovoljno:
// Europe/Zagreb i Europe/Sarajevo su različita imena za ista vremena.
export function zonaUredjajaRazlicita(trenuci, zona, zonaUredjaja = Intl.DateTimeFormat().resolvedOptions().timeZone) {
	return trenuci.some((t) => satUZoni(t, zona) !== satUZoni(t, zonaUredjaja) || danUZoni(t, zona) !== danUZoni(t, zonaUredjaja))
}
