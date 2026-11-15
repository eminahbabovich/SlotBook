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
