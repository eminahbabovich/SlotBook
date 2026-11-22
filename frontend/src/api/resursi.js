import { apiFetch, provjeriPolja } from './apiFetch.js'

// ResourceResponse. active mora stići: admin i provider vide i deaktivirane resurse, a bez
// polja bi deaktiviran izgledao kao aktivan.
function izOdgovora(r, putanja) {
	provjeriPolja(r, ['id', 'name', 'slotMinutes', 'autoConfirm', 'cancelNoticeHours', 'capacity', 'active'], putanja)
	return {
		id: r.id,
		naziv: r.name,
		opis: r.description ?? null,
		trajanjeMinuta: r.slotMinutes,
		vodi: r.providerId ?? null,
		autoPotvrda: r.autoConfirm,
		rokOtkazivanjaSati: r.cancelNoticeHours,
		brojMjesta: r.capacity,
		aktivan: r.active,
	}
}

// Iz PageResponse. Organizacija s više od 100 resursa za sada ne postoji, a lista za izbor
// ionako ne bi bila čitljiva. Kad zatreba, ide pretraga, ne stranice.
export async function resursiOrganizacije(organizacijaId) {
	const putanja = `/api/organizations/${organizacijaId}/resources?size=100`
	const stranica = provjeriPolja(await apiFetch(putanja), ['content'], putanja)
	return stranica.content.map((r) => izOdgovora(r, putanja))
}

// Polje forme je tekst. Prazno ide kao null, da backend vrati 400 za to polje (trajanje) ili
// uzme podrazumijevanu vrijednost (rok), umjesto da Number('') pošalje 0.
const broj = (vrijednost) => (vrijednost === '' || vrijednost === null ? null : Number(vrijednost))

// ResourceRequest. Šalju se sva polja i kod izmjene, jer PUT bez autoConfirm, cancelNoticeHours
// ili capacity vrati ta polja na podrazumijevane vrijednosti (sala s 5 mjesta bi postala 1).
const zahtjev = (r) => ({
	name: r.naziv,
	description: r.opis || null,
	slotMinutes: broj(r.trajanjeMinuta),
	autoConfirm: r.autoPotvrda,
	cancelNoticeHours: broj(r.rokOtkazivanjaSati),
	capacity: broj(r.brojMjesta),
})

export async function noviResurs(organizacijaId, resurs) {
	const putanja = `/api/organizations/${organizacijaId}/resources`
	return izOdgovora(await apiFetch(putanja, { metoda: 'POST', tijelo: zahtjev(resurs) }), putanja)
}

export async function izmijeniResurs(organizacijaId, resursId, resurs) {
	const putanja = `/api/organizations/${organizacijaId}/resources/${resursId}`
	return izOdgovora(await apiFetch(putanja, { metoda: 'PUT', tijelo: zahtjev(resurs) }), putanja)
}

// Resurs se ne briše, nego deaktivira (204). Rezervacije i statistika ostaju.
export async function deaktivirajResurs(organizacijaId, resursId) {
	await apiFetch(`/api/organizations/${organizacijaId}/resources/${resursId}`, { metoda: 'DELETE' })
}

// Samo admin. Novi voditelj mora biti ADMIN ili PROVIDER u istoj organizaciji, inače 400.
export async function promijeniVoditelja(organizacijaId, resursId, korisnikId) {
	const putanja = `/api/organizations/${organizacijaId}/resources/${resursId}/provider`
	return izOdgovora(await apiFetch(putanja, { metoda: 'PUT', tijelo: { providerId: korisnikId } }), putanja)
}
