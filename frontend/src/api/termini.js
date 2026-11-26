import { pratiTok } from './tok.js'
import { apiFetch, provjeriPolja } from './apiFetch.js'

const STANJE_MOJE = { CONFIRMED: 'moje', PENDING: 'ceka' }

// SlotCalendarResponse(timezone, capacity, slots, taken, offeredToMe). od i doDana su dani u zoni
// organizacije, a doDana se ne uključuje, kao u backendu. upisId je moj upis na listu čekanja za taj
// termin. Kod grupnog termina (capacity > 1) slobodan ima broj mjesta koja mogu dobiti, a zauzet je pun.
export async function kalendarResursa(resursId, od, doDana) {
	const putanja = `/api/resources/${resursId}/slots?from=${od}&to=${doDana}`
	const odgovor = provjeriPolja(await apiFetch(putanja), ['timezone', 'slots', 'taken'], putanja)
	// offeredToMe nije obavezno: bez njega ponuđen termin ostane običan slobodan, a to je za mene i tačno
	const ponude = new Map((odgovor.offeredToMe ?? []).map((p) => [p.start, p]))
	const grupni = (odgovor.capacity ?? 1) > 1
	const termini = [
		...odgovor.slots.map((t) => {
			const ponuda = ponude.get(t.start)
			return ponuda
				? { pocetak: t.start, kraj: t.end, stanje: 'ponudjen', rokPonude: ponuda.offeredUntil, upisId: ponuda.waitlistId }
				: { pocetak: t.start, kraj: t.end, stanje: 'slobodno', mjesta: grupni ? t.seatsLeft : null }
		}),
		...odgovor.taken.map((t) => ({
			pocetak: t.start,
			kraj: t.end,
			stanje: STANJE_MOJE[t.myStatus] ?? 'zauzeto',
			upisId: t.myWaitlistId ?? null,
			pun: grupni && !t.myStatus,
			uplata: t.myAwaitingPayment === true,
		})),
	]
	// ISO stringovi u UTC-u se porede kao tekst, isto kao vremena
	termini.sort((a, b) => a.pocetak.localeCompare(b.pocetak))
	return { zona: odgovor.timezone, termini }
}

// SSE tok termina resursa (pratiTok): javlja samo da su se termini promijenili, podatke ne nosi
export function pratiTermine(resursId, opcije) {
	return pratiTok(`/api/resources/${resursId}/slots/stream`, opcije)
}

// OrganizationFreeSlotsResponse iz GET /api/organizations/{id}/free-slots ("Nađi slobodno"). Backend
// vraća samo slobodne termine aktivnih resursa, poredane po početku, i klijentu samo one koje smije uzeti.
export async function slobodnoUOrganizaciji(organizacijaId, { dan, odSata, doSata }) {
	const putanja = `/api/organizations/${organizacijaId}/free-slots?date=${dan}&days=1&fromHour=${odSata}&toHour=${doSata}`
	const odgovor = provjeriPolja(await apiFetch(putanja), ['timezone', 'slots'], putanja)
	return {
		zona: odgovor.timezone,
		termini: odgovor.slots.map((t) => ({
			resursId: t.resourceId,
			resurs: t.resourceName,
			pocetak: t.start,
			kraj: t.end,
			mjesta: t.capacity > 1 ? t.seatsLeft : null,
		})),
	}
}
