import { apiFetch, provjeriPolja } from './apiFetch.js'

// BookingRequest(startTime) -> WaitlistEntryResponse. Ponovljen upis vraća isti upis, pa dupli klik ne smeta.
export async function upisiNaListu(resursId, pocetak) {
	const putanja = `/api/resources/${resursId}/waitlist`
	const r = provjeriPolja(await apiFetch(putanja, { metoda: 'POST', tijelo: { startTime: pocetak } }), ['id', 'status'], putanja)
	return { id: r.id, status: r.status }
}

export async function ispisiSaListe(upisId) {
	await apiFetch(`/api/waitlist/${upisId}`, { metoda: 'DELETE' })
}

const POLJA = ['id', 'resourceId', 'resourceName', 'organizationName', 'organizationSlug', 'timezone', 'startTime', 'endTime', 'status']

// PageResponse<MyWaitlistEntryResponse>, samo živi upisi (WAITING, OFFERED), najbliži termin prvo
export async function mojaListaCekanja(stranica) {
	const putanja = `/api/waitlist/mine?page=${stranica}&size=20`
	const odgovor = provjeriPolja(await apiFetch(putanja), ['content', 'page', 'totalPages'], putanja)
	return {
		upisi: odgovor.content.map((u) => {
			provjeriPolja(u, POLJA, putanja)
			return {
				id: u.id,
				resursId: u.resourceId,
				resurs: u.resourceName,
				organizacija: u.organizationName,
				slug: u.organizationSlug,
				zona: u.timezone,
				pocetak: u.startTime,
				kraj: u.endTime,
				status: u.status,
				rokPonude: u.offeredUntil ?? null,
			}
		}),
		stranica: odgovor.page,
		ukupnoStranica: odgovor.totalPages,
	}
}
