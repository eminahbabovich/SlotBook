import { afterEach, expect, test, vi } from 'vitest'
import { ApiError, postaviToken } from './apiFetch.js'
import { odbaciPrijedlog, otvoriRazgovor, posaljiPoruku, potvrdiPrijedlog } from './asistent.js'

const json = (tijelo, status = 200) => new Response(JSON.stringify(tijelo), { status, headers: { 'Content-Type': 'application/json' } })

afterEach(() => {
	vi.unstubAllGlobals()
	postaviToken(null)
})

// Oblik kao u ChatApiTest.modelKojiKazeDaJeRezervisaoNePraviRezervacijuBezDugmeta
const PRIJEDLOG = {
	id: 'p1',
	action: 'BOOK',
	resourceId: 'r1',
	resourceName: 'Sto 4, tihi dio',
	startTime: '2035-03-26T08:00:00Z',
	endTime: '2035-03-26T09:00:00Z',
	needsProviderConfirmation: false,
	bookingId: null,
}

test('razgovor se otvara u organizaciji i vraća svoj id', async () => {
	const fetch = vi.fn(async () => json({ id: 's1', createdAt: '2035-03-25T10:00:00Z' }, 201))
	vi.stubGlobal('fetch', fetch)

	expect(await otvoriRazgovor('o1')).toBe('s1')
	expect(fetch.mock.calls[0][0]).toBe('/api/organizations/o1/chat/sessions')
	expect(fetch.mock.calls[0][1].method).toBe('POST')
})

test('poruka šalje text, a odgovor daje tekst i kartu prijedloga s poljima koja widget crta', async () => {
	const fetch = vi.fn(async () => json({ text: 'Sto 4 je slobodan u 10:00.', proposals: [PRIJEDLOG, { ...PRIJEDLOG, id: 'p2', action: 'CANCEL', bookingId: 'b7', needsProviderConfirmation: false }] }))
	vi.stubGlobal('fetch', fetch)

	const odgovor = await posaljiPoruku('s1', 'Sto 4 u 10?')

	expect(fetch.mock.calls[0][0]).toBe('/api/chat/sessions/s1/messages')
	expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({ text: 'Sto 4 u 10?' })
	expect(odgovor.tekst).toBe('Sto 4 je slobodan u 10:00.')
	expect(odgovor.prijedlozi[0]).toEqual({
		id: 'p1',
		akcija: 'BOOK',
		resursId: 'r1',
		resurs: 'Sto 4, tihi dio',
		pocetak: '2035-03-26T08:00:00Z',
		kraj: '2035-03-26T09:00:00Z',
		cekaPotvrdu: false,
		rezervacijaId: null,
	})
	expect(odgovor.prijedlozi[1].rezervacijaId).toBe('b7')
})

test('prijedlog bez vremena glasno pada, umjesto da karta pokaže prazan sat', async () => {
	// JSON.stringify izbaci polje s undefined, pa backend "ne pošalje" startTime
	const bezPocetka = { ...PRIJEDLOG, startTime: undefined }
	vi.stubGlobal('fetch', vi.fn(async () => json({ text: 'Evo.', proposals: [bezPocetka] })))

	await expect(posaljiPoruku('s1', 'Sto 4?')).rejects.toThrow('nema polja startTime')
})

test('potrošena granica stiže kao ApiError s Retry-After, da widget kaže kad opet radi', async () => {
	vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ status: 429, title: 'Asistent je za danas potrošen', detail: 'Tvoja dnevna granica poruka asistentu je potrošena.' }), { status: 429, headers: { 'Content-Type': 'application/problem+json', 'Retry-After': '3600' } })))

	const greska = await posaljiPoruku('s1', 'Ima li?').catch((e) => e)

	expect(greska).toBeInstanceOf(ApiError)
	expect(greska.status).toBe(429)
	expect(greska.retryAfter).toBe(3600)
})

test('potvrda vraća rezervaciju i njen status, a odbacivanje ide na dismiss', async () => {
	const fetch = vi.fn(async (putanja) =>
		putanja.endsWith('/confirm') ? json({ id: 'p1', action: 'BOOK', bookingId: 'b1', bookingStatus: 'PENDING' }) : new Response(null, { status: 204 }),
	)
	vi.stubGlobal('fetch', fetch)

	expect(await potvrdiPrijedlog('p1')).toEqual({ akcija: 'BOOK', rezervacijaId: 'b1', status: 'PENDING' })
	await odbaciPrijedlog('p2')
	expect(fetch.mock.calls[1][0]).toBe('/api/chat/proposals/p2/dismiss')
	expect(fetch.mock.calls[1][1].method).toBe('POST')
})

test('podebljanje iz modela se skine, da zvjezdice ne ostanu u tekstu', async () => {
	vi.stubGlobal('fetch', vi.fn(async () => json({ text: 'Sutra ujutro je slobodan **Sto 4, tihi dio** u 08:00.', proposals: [] })))

	expect((await posaljiPoruku('s1', 'Sutra?')).tekst).toBe('Sutra ujutro je slobodan Sto 4, tihi dio u 08:00.')
})
