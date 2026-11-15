import { afterEach, expect, test, vi } from 'vitest'
import { postaviToken } from './apiFetch.js'
import { kalendarResursa, resursiOrganizacije } from './termini.js'

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

test('kalendar bez polja taken glasno pada, umjesto da sve zauzeto prikaže kao slobodno', async () => {
	vi.stubGlobal('fetch', vi.fn(async () => json({ timezone: 'Europe/Sarajevo', slots: [] })))

	await expect(kalendarResursa('r1', '2035-11-19', '2035-11-26')).rejects.toThrow(/taken/)
})

test('resursi organizacije dolaze s nazivom, opisom i trajanjem termina', async () => {
	const fetch = vi.fn(async () =>
		json({
			content: [{ id: 'r1', organizationId: 'o1', providerId: 'u1', name: 'Sto 4, tihi dio', description: null, slotMinutes: 60, autoConfirm: true, cancelNoticeHours: 0, active: true, hasImage: false }],
			page: 0, size: 100, totalElements: 1, totalPages: 1,
		}),
	)
	vi.stubGlobal('fetch', fetch)

	expect(await resursiOrganizacije('o1')).toEqual([{ id: 'r1', naziv: 'Sto 4, tihi dio', opis: null, trajanjeMinuta: 60 }])
	expect(fetch.mock.calls[0][0]).toBe('/api/organizations/o1/resources?size=100')
})
