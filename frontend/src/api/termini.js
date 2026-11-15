import { apiFetch, provjeriPolja } from './apiFetch.js'

// ResourceResponse iz PageResponse. Organizacija s više od 100 resursa za sada ne postoji,
// a lista za izbor ionako ne bi bila čitljiva. Kad zatreba, ide pretraga, ne stranice.
export async function resursiOrganizacije(organizacijaId) {
	const putanja = `/api/organizations/${organizacijaId}/resources?size=100`
	const stranica = provjeriPolja(await apiFetch(putanja), ['content'], putanja)
	return stranica.content.map((r) => {
		provjeriPolja(r, ['id', 'name', 'slotMinutes'], putanja)
		return { id: r.id, naziv: r.name, opis: r.description ?? null, trajanjeMinuta: r.slotMinutes }
	})
}

const STANJE_MOJE = { CONFIRMED: 'moje', PENDING: 'ceka' }

// SlotCalendarResponse(timezone, slots, taken). od i doDana su dani u zoni organizacije,
// a doDana se ne uključuje, kao u backendu.
export async function kalendarResursa(resursId, od, doDana) {
	const putanja = `/api/resources/${resursId}/slots?from=${od}&to=${doDana}`
	const odgovor = provjeriPolja(await apiFetch(putanja), ['timezone', 'slots', 'taken'], putanja)
	const termini = [
		...odgovor.slots.map((t) => ({ pocetak: t.start, kraj: t.end, stanje: 'slobodno' })),
		...odgovor.taken.map((t) => ({ pocetak: t.start, kraj: t.end, stanje: STANJE_MOJE[t.myStatus] ?? 'zauzeto' })),
	]
	// ISO stringovi u UTC-u se porede kao tekst, isto kao vremena
	termini.sort((a, b) => a.pocetak.localeCompare(b.pocetak))
	return { zona: odgovor.timezone, termini }
}
