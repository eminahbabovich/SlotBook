import { apiFetch, provjeriPolja } from './apiFetch.js'

// List<ResourceBookingResponse> za dane od..doDana u zoni organizacije (doDana se ne uključuje).
// Vide ga samo admin i provider resursa, jer su u njemu imena klijenata. awaitingPayment mora stići:
// bez njega bi raspored nudio "Potvrdi" za plaćenu rezervaciju, a backend to odbija s 409.
export async function rasporedResursa(resursId, od, doDana) {
	const putanja = `/api/resources/${resursId}/bookings?from=${od}&to=${doDana}`
	const odgovor = await apiFetch(putanja)
	if (!Array.isArray(odgovor)) throw new Error(`${putanja}: očekivana lista rezervacija`)
	return odgovor.map((r) => {
		provjeriPolja(r, ['id', 'startTime', 'endTime', 'status', 'clientName', 'awaitingPayment'], putanja)
		return {
			id: r.id,
			pocetak: r.startTime,
			kraj: r.endTime,
			status: r.status,
			klijent: r.clientName,
			rokPotvrde: r.expiresAt ?? null,
			cekaUplatu: r.awaitingPayment,
		}
	})
}

// Svaki prelaz ima svoju adresu na backendu (BookingController), pa i svoje pravilo ko ga smije
const ADRESE = { potvrdi: 'confirm', odbij: 'reject', dosao: 'check-in', nijeDosao: 'no-show' }

export async function promijeniStatus(rezervacijaId, akcija) {
	const putanja = `/api/bookings/${rezervacijaId}/${ADRESE[akcija]}`
	return provjeriPolja(await apiFetch(putanja, { metoda: 'POST' }), ['id', 'status'], putanja)
}
