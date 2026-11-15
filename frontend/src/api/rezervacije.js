import { apiFetch, provjeriPolja } from './apiFetch.js'

// BookingRequest(startTime) -> BookingResponse(id, resourceId, startTime, endTime, status, expiresAt).
// kljuc je Idempotency-Key: isti ključ za istu namjeru, pa ponovljen zahtjev ne pravi drugu
// rezervaciju, nego backend vrati onu prvu.
export async function rezervisi(resursId, pocetak, kljuc) {
	const putanja = `/api/resources/${resursId}/bookings`
	const r = await apiFetch(putanja, { metoda: 'POST', tijelo: { startTime: pocetak }, zaglavlja: { 'Idempotency-Key': kljuc } })
	provjeriPolja(r, ['id', 'startTime', 'endTime', 'status'], putanja)
	return { id: r.id, pocetak: r.startTime, kraj: r.endTime, status: r.status, rokPotvrde: r.expiresAt ?? null }
}

const POLJA_MOJE = ['id', 'resourceName', 'organizationName', 'timezone', 'startTime', 'endTime', 'status', 'cancelDeadline']

// PageResponse<MyBookingResponse>, najkasniji termin prvo (redoslijed bira backend)
export async function mojeRezervacije(stranica) {
	const putanja = `/api/bookings/mine?page=${stranica}&size=20`
	const odgovor = provjeriPolja(await apiFetch(putanja), ['content', 'page', 'totalPages'], putanja)
	return {
		rezervacije: odgovor.content.map((r) => {
			provjeriPolja(r, POLJA_MOJE, putanja)
			return {
				id: r.id,
				resurs: r.resourceName,
				organizacija: r.organizationName,
				zona: r.timezone,
				pocetak: r.startTime,
				kraj: r.endTime,
				status: r.status,
				rokOtkazivanja: r.cancelDeadline,
			}
		}),
		stranica: odgovor.page,
		ukupnoStranica: odgovor.totalPages,
	}
}

export async function otkazi(rezervacijaId) {
	const putanja = `/api/bookings/${rezervacijaId}/cancel`
	const r = await apiFetch(putanja, { metoda: 'POST' })
	return provjeriPolja(r, ['id', 'status'], putanja)
}
