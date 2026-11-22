import { apiFetch, provjeriPolja } from './apiFetch.js'

const STANJE_MOJE = { CONFIRMED: 'moje', PENDING: 'ceka' }

// SlotCalendarResponse(timezone, slots, taken, offeredToMe). od i doDana su dani u zoni organizacije,
// a doDana se ne uključuje, kao u backendu. upisId je moj upis na listu čekanja za taj termin.
export async function kalendarResursa(resursId, od, doDana) {
	const putanja = `/api/resources/${resursId}/slots?from=${od}&to=${doDana}`
	const odgovor = provjeriPolja(await apiFetch(putanja), ['timezone', 'slots', 'taken'], putanja)
	// offeredToMe nije obavezno: bez njega ponuđen termin ostane običan slobodan, a to je za mene i tačno
	const ponude = new Map((odgovor.offeredToMe ?? []).map((p) => [p.start, p]))
	const termini = [
		...odgovor.slots.map((t) => {
			const ponuda = ponude.get(t.start)
			return ponuda
				? { pocetak: t.start, kraj: t.end, stanje: 'ponudjen', rokPonude: ponuda.offeredUntil, upisId: ponuda.waitlistId }
				: { pocetak: t.start, kraj: t.end, stanje: 'slobodno' }
		}),
		...odgovor.taken.map((t) => ({
			pocetak: t.start,
			kraj: t.end,
			stanje: STANJE_MOJE[t.myStatus] ?? 'zauzeto',
			upisId: t.myWaitlistId ?? null,
		})),
	]
	// ISO stringovi u UTC-u se porede kao tekst, isto kao vremena
	termini.sort((a, b) => a.pocetak.localeCompare(b.pocetak))
	return { zona: odgovor.timezone, termini }
}
