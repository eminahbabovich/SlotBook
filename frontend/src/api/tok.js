import { apiFetch } from './apiFetch.js'

// SSE tok koji samo javlja "promjena". EventSource ne može poslati Authorization zaglavlje, pa se tok
// čita kroz fetch. Završi se kad server zatvori vezu (poslije 15 minuta), a baci ApiError kad se ne
// otvori. naSpajanje javi da je veza otvorena, naPromjenu da su se termini promijenili.
export async function pratiTok(putanja, { naSpajanje, naPromjenu, signal }) {
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

const PRVA_PAUZA = 1000
const NAJDUZA_PAUZA = 30_000

function cekaj(ms, signal) {
	return new Promise((gotovo) => {
		if (signal.aborted) return gotovo()
		const sat = setTimeout(gotovo, ms)
		signal.addEventListener('abort', () => {
			clearTimeout(sat)
			gotovo()
		})
	})
}

// Drži tok otvoren dok signal ne prekine. Kad veza pukne, spaja se ponovo, s pauzom koja raste do
// 30 s, da server koji ne radi ne dobija zahtjev svake sekunde. Poslije ponovnog spajanja javi
// naPonovnoSpajanje, jer promjene dok veze nije bilo nisu stizale. naVezu(true/false) je za prikaz.
export async function drziVezu(prati, { naPromjenu, naPonovnoSpajanje, naVezu = () => {} }, signal) {
	let pauza = PRVA_PAUZA
	let prvaVeza = true
	while (!signal.aborted) {
		try {
			await prati({
				signal,
				naSpajanje: () => {
					if (!prvaVeza) naPonovnoSpajanje()
					prvaVeza = false
					pauza = PRVA_PAUZA
					naVezu(true)
				},
				naPromjenu,
			})
		} catch {
			pauza = Math.min(pauza * 2, NAJDUZA_PAUZA)
		}
		if (!signal.aborted) naVezu(false)
		await cekaj(pauza, signal)
	}
}
