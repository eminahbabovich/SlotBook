import { danUZoni, dodajDane } from '../vrijeme/vrijeme.js'

// Termini koje klijent po pravilima resursa ne može uzeti se ne nude: slobodan termin prerano
// (minutaPrije) ili predaleko (danaUnaprijed, po kalendaru u zoni organizacije, kao na backendu).
// Svoje i tuđe zauzete termine klijent i dalje vidi. Granicu budućih rezervacija provjerava backend,
// jer zavisi od rezervacija na drugim sedmicama.
export function uPravilima(kalendar, pravila, sada) {
	if (!pravila || (pravila.minutaPrije == null && pravila.danaUnaprijed == null)) return kalendar
	const { zona } = kalendar
	const najranije = new Date(sada).getTime() + (pravila.minutaPrije ?? 0) * 60_000
	const zadnjiDan = pravila.danaUnaprijed == null ? null : dodajDane(danUZoni(sada, zona), pravila.danaUnaprijed)
	const nudi = (t) => {
		if (!['slobodno', 'ponudjen'].includes(t.stanje)) return true
		if (new Date(t.pocetak).getTime() < najranije) return false
		return zadnjiDan === null || danUZoni(t.pocetak, zona) <= zadnjiDan
	}
	return { ...kalendar, termini: kalendar.termini.filter(nudi) }
}

// Kratko, za značke na stranici resursa
export function opisiPravila(pravila) {
	if (!pravila) return []
	const opisi = []
	if (pravila.najviseAktivnih != null) opisi.push(`najviše ${pravila.najviseAktivnih} unaprijed po osobi`)
	if (pravila.danaUnaprijed != null) opisi.push(`do ${pravila.danaUnaprijed} ${pravila.danaUnaprijed === 1 ? 'dan' : 'dana'} unaprijed`)
	if (pravila.minutaPrije != null) opisi.push(`najkasnije ${trajanje(pravila.minutaPrije)} prije`)
	return opisi
}

function trajanje(minuta) {
	if (minuta % 60 !== 0) return `${minuta} min`
	const sati = minuta / 60
	return sati === 1 ? 'sat' : `${sati} h`
}
