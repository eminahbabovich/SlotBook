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

// BookingSeriesRequest(startTime, weeks) -> BookingSeriesResponse(seriesId, bookings). Sve ili ništa:
// kad neka sedmica nije slobodna, 409 ima zauzetiTermini i nijedna rezervacija nije napravljena.
export async function rezervisiSeriju(resursId, pocetak, sedmica, kljuc) {
	const putanja = `/api/resources/${resursId}/bookings/series`
	const r = provjeriPolja(
		await apiFetch(putanja, { metoda: 'POST', tijelo: { startTime: pocetak, weeks: sedmica }, zaglavlja: { 'Idempotency-Key': kljuc } }),
		['seriesId', 'bookings'],
		putanja,
	)
	return r.bookings.map((b) => {
		provjeriPolja(b, ['id', 'startTime', 'endTime', 'status'], putanja)
		return { id: b.id, pocetak: b.startTime, kraj: b.endTime, status: b.status, rokPotvrde: b.expiresAt ?? null }
	})
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

// CheckInLinkResponse(url). Link traži samo klijent, jer je QR kod njegov dokaz dolaska.
export async function linkZaDolazak(rezervacijaId) {
	const putanja = `/api/bookings/${rezervacijaId}/check-in-link`
	return provjeriPolja(await apiFetch(putanja), ['url'], putanja).url
}

// QrCheckInRequest(token) -> BookingResponse. Šalje onaj ko vodi resurs, s kodom iz skeniranog linka.
export async function upisiDolazakSKodom(rezervacijaId, kod) {
	const putanja = `/api/bookings/${rezervacijaId}/check-in/qr`
	return provjeriPolja(await apiFetch(putanja, { metoda: 'POST', tijelo: { token: kod } }), ['id', 'status'], putanja)
}
