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
		})),
	]
	// ISO stringovi u UTC-u se porede kao tekst, isto kao vremena
	termini.sort((a, b) => a.pocetak.localeCompare(b.pocetak))
	return { zona: odgovor.timezone, termini }
}

// SSE tok termina resursa. EventSource ne može poslati Authorization zaglavlje, pa se tok čita
// kroz fetch. Završi se kad server zatvori vezu (poslije 15 minuta), a baci ApiError kad se ne otvori.
// naSpajanje javi da je veza otvorena, naPromjenu da su se termini promijenili. Podatke tok ne nosi.
export async function pratiTermine(resursId, { naSpajanje, naPromjenu, signal }) {
	const putanja = `/api/resources/${resursId}/slots/stream`
	const odgovor = await apiFetch(putanja, { oblik: 'tok', signal, zaglavlja: { Accept: 'text/event-stream' } })
	naSpajanje()
	const citac = odgovor.body.getReader()
	const dekoder = new TextDecoder()
	let ostatak = ''
	for (;;) {
		const { value, done } = await citac.read()
		if (done) return
		ostatak += dekoder.decode(value, { stream: true })
		// Poruka završava praznim redom. Zadnji dio može biti pola poruke, pa čeka sljedeći komad.
		const poruke = ostatak.split('\n\n')
		ostatak = poruke.pop()
		// Spring piše "event:promjena", a standard dozvoljava i razmak iza dvotačke
		if (poruke.some((poruka) => poruka.split('\n').some((red) => /^event: ?promjena$/.test(red)))) {
			naPromjenu()
		}
	}
}
