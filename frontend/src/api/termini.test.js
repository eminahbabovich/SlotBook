import { afterEach, expect, test, vi } from 'vitest'
import { postaviToken } from './apiFetch.js'
import { kalendarResursa } from './termini.js'

const json = (tijelo) => new Response(JSON.stringify(tijelo), { status: 200, headers: { 'Content-Type': 'application/json' } })

afterEach(() => {
	vi.unstubAllGlobals()
	postaviToken(null)
})

// Oblik kao u BookingApiTest: Hana ima 08:00Z (svoju), 09:00Z čeka potvrdu, 10:00Z je tuđi.
test('kalendar spaja slobodne i zauzete termine po vremenu, sa stanjem svakog', async () => {
	const fetch = vi.fn(async () =>
		json({
			timezone: 'Europe/Sarajevo',
			slots: [
				{ start: '2035-11-19T07:00:00Z', end: '2035-11-19T08:00:00Z' },
				{ start: '2035-11-19T11:00:00Z', end: '2035-11-19T12:00:00Z' },
			],
			taken: [
				{ start: '2035-11-19T08:00:00Z', end: '2035-11-19T09:00:00Z', myStatus: 'CONFIRMED' },
				{ start: '2035-11-19T09:00:00Z', end: '2035-11-19T10:00:00Z', myStatus: 'PENDING' },
				{ start: '2035-11-19T10:00:00Z', end: '2035-11-19T11:00:00Z', myStatus: null },
			],
		}),
	)
	vi.stubGlobal('fetch', fetch)

	const kalendar = await kalendarResursa('r1', '2035-11-19', '2035-11-26')

	expect(fetch.mock.calls[0][0]).toBe('/api/resources/r1/slots?from=2035-11-19&to=2035-11-26')
	expect(kalendar.zona).toBe('Europe/Sarajevo')
	expect(kalendar.termini.map((t) => [t.pocetak.slice(11, 16), t.stanje])).toEqual([
		['07:00', 'slobodno'],
		['08:00', 'moje'],
		['09:00', 'ceka'],
		['10:00', 'zauzeto'],
		['11:00', 'slobodno'],
	])
	expect(kalendar.termini[0].kraj).toBe('2035-11-19T08:00:00Z')
})

// Oblik kao u BookingApiTest: da li moja čeka uplatu kaže server (cijena na rezervaciji), ne cijena resursa
test('moja rezervacija čeka uplatu samo kad to kaže server', async () => {
	vi.stubGlobal('fetch', vi.fn(async () =>
		json({
			timezone: 'Europe/Sarajevo',
			slots: [],
			taken: [
				{ start: '2035-11-19T09:00:00Z', end: '2035-11-19T10:00:00Z', myStatus: 'PENDING', myAwaitingPayment: true },
				{ start: '2035-11-19T10:00:00Z', end: '2035-11-19T11:00:00Z', myStatus: 'PENDING', myAwaitingPayment: false },
			],
		}),
	))

	const kalendar = await kalendarResursa('r1', '2035-11-19', '2035-11-26')

	expect(kalendar.termini.map((t) => [t.stanje, t.uplata])).toEqual([['ceka', true], ['ceka', false]])
})

// Oblik kao u WaitlistApiTest za salu s dva mjesta
test('grupni termin nosi broj mjesta koja ostaju, a običan ne', async () => {
	const odgovor = (capacity, seatsLeft) =>
		json({ timezone: 'Europe/Sarajevo', capacity, slots: [{ start: '2035-11-19T07:00:00Z', end: '2035-11-19T08:00:00Z', seatsLeft }], taken: [{ start: '2035-11-19T08:00:00Z', end: '2035-11-19T09:00:00Z', myStatus: null }] })
	vi.stubGlobal('fetch', vi.fn(async () => odgovor(2, 1)))
	const grupni = await kalendarResursa('r1', '2035-11-19', '2035-11-26')
	vi.stubGlobal('fetch', vi.fn(async () => odgovor(1, 1)))
	const obican = await kalendarResursa('r1', '2035-11-19', '2035-11-26')

	expect(grupni.termini.map((t) => [t.mjesta ?? null, t.pun ?? null])).toEqual([[1, null], [null, true]])
	expect(obican.termini.map((t) => [t.mjesta ?? null, t.pun ?? null])).toEqual([[null, null], [null, false]])
})

test('kalendar bez polja taken glasno pada, umjesto da sve zauzeto prikaže kao slobodno', async () => {
	vi.stubGlobal('fetch', vi.fn(async () => json({ timezone: 'Europe/Sarajevo', slots: [] })))

	await expect(kalendarResursa('r1', '2035-11-19', '2035-11-26')).rejects.toThrow(/taken/)
})
