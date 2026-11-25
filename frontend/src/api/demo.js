import { ApiError, apiFetch, provjeriPolja } from './apiFetch.js'

// DemoResponse(lozinka, racuni: [DemoRacun(email, ime, opis)]) iz ba.slotbook.demo.
// Van profila demo backend nema ovaj endpoint i vraća 404, pa funkcija vrati null.
export async function demoRacuni() {
	try {
		const odgovor = provjeriPolja(await apiFetch('/api/demo'), ['lozinka', 'racuni'], '/api/demo')
		return {
			lozinka: odgovor.lozinka,
			racuni: odgovor.racuni.map((r) => ({ email: r.email, ime: r.ime, opis: r.opis })),
		}
	} catch (greska) {
		if (greska instanceof ApiError && greska.status === 404) return null
		throw greska
	}
}
