import { afterEach, expect, test, vi } from 'vitest'
import { postaviToken } from './apiFetch.js'
import { csvStatistike, statistika } from './statistika.js'

const json = (tijelo, status = 200) => new Response(JSON.stringify(tijelo), { status, headers: { 'Content-Type': 'application/json' } })

afterEach(() => {
	vi.unstubAllGlobals()
	postaviToken(null)
})

// Prepisano iz StatsApiTest (admin, novembar 2035)
const ODGOVOR = {
	from: '2035-11-01',
	to: '2035-12-01',
	timezone: 'Europe/Sarajevo',
	byStatus: { PENDING: 1, CONFIRMED: 2, REJECTED: 0, EXPIRED: 0, CANCELLED: 1, COMPLETED: 2, NO_SHOW: 1 },
	byDayAndHour: [
		{ dayOfWeek: 1, hour: 8, count: 3 },
		{ dayOfWeek: 2, hour: 14, count: 1 },
	],
	occupancy: [{ resourceId: 'r1', name: 'Sto br. 1', bookedMinutes: 240, offeredMinutes: 840 }],
}

test('statistika za mjesec traži period [od, do) i vraća polja kako ih čita ekran', async () => {
	const fetch = vi.fn(async () => json(ODGOVOR))
	vi.stubGlobal('fetch', fetch)

	expect(await statistika('o1', '2035-11-01', '2035-12-01')).toEqual({
		zona: 'Europe/Sarajevo',
		poStanju: ODGOVOR.byStatus,
		poDanuISatu: [
			{ dan: 1, sat: 8, broj: 3 },
			{ dan: 2, sat: 14, broj: 1 },
		],
		popunjenost: [{ resursId: 'r1', naziv: 'Sto br. 1', zauzeto: 240, ponudjeno: 840 }],
	})
	expect(fetch.mock.calls[0][0]).toBe('/api/organizations/o1/stats?from=2035-11-01&to=2035-12-01')
})

test('odgovor bez popunjenosti je greška na granici, ne prazna tabela', async () => {
	vi.stubGlobal('fetch', vi.fn(async () => json({ ...ODGOVOR, occupancy: undefined })))

	await expect(statistika('o1', '2035-11-01', '2035-12-01')).rejects.toThrow(/occupancy/)
})

// Body.text() dekodira UTF-8 i usput baci BOM. Bez BOM-a Excel č i ć čita kao dva znaka.
test('CSV stiže kao bajtovi s BOM-om, uz Accept: text/csv na istoj putanji', async () => {
	const bajtovi = new Uint8Array([0xef, 0xbb, 0xbf, ...new TextEncoder().encode('Resurs;Zauzeto (min)\r\nČitaonica;240\r\n')])
	const fetch = vi.fn(async () => new Response(bajtovi, { status: 200, headers: { 'Content-Type': 'text/csv;charset=UTF-8' } }))
	vi.stubGlobal('fetch', fetch)

	const fajl = await csvStatistike('o1', '2035-11-01', '2035-12-01')

	expect([...new Uint8Array(await fajl.arrayBuffer()).slice(0, 3)]).toEqual([0xef, 0xbb, 0xbf])
	const [putanja, opcije] = fetch.mock.calls[0]
	expect(putanja).toBe('/api/organizations/o1/stats?from=2035-11-01&to=2035-12-01')
	expect(opcije.headers.Accept).toMatch(/^text\/csv/)
	expect(opcije.headers.Accept).toContain('application/problem+json')
})
