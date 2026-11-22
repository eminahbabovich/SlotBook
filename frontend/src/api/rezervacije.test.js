import { afterEach, expect, test, vi } from 'vitest'
import { postaviToken } from './apiFetch.js'
import { mojeRezervacije, otkazi, rezervisi, rezervisiSeriju } from './rezervacije.js'

afterEach(() => {
	vi.unstubAllGlobals()
	postaviToken(null)
})

// Oblik kao BookingResponse u BookingApiTest
test('rezervacija šalje početak i Idempotency-Key, a vraća status i rok za potvrdu', async () => {
	const fetch = vi.fn(async () =>
		new Response(
			JSON.stringify({ id: 'b1', resourceId: 'r1', startTime: '2035-11-19T07:00:00Z', endTime: '2035-11-19T08:00:00Z', status: 'PENDING', expiresAt: '2035-11-18T07:00:00Z' }),
			{ status: 201, headers: { 'Content-Type': 'application/json' } },
		),
	)
	vi.stubGlobal('fetch', fetch)

	const rezervacija = await rezervisi('r1', '2035-11-19T07:00:00Z', 'kljuc-1')

	const [putanja, opcije] = fetch.mock.calls[0]
	expect(putanja).toBe('/api/resources/r1/bookings')
	expect(opcije.method).toBe('POST')
	expect(JSON.parse(opcije.body)).toEqual({ startTime: '2035-11-19T07:00:00Z' })
	expect(opcije.headers['Idempotency-Key']).toBe('kljuc-1')
	expect(rezervacija).toEqual({ id: 'b1', pocetak: '2035-11-19T07:00:00Z', kraj: '2035-11-19T08:00:00Z', status: 'PENDING', rokPotvrde: '2035-11-18T07:00:00Z' })
})

test('moje rezervacije dolaze s nazivom resursa i organizacije, zonom i rokom za otkazivanje', async () => {
	const fetch = vi.fn(async () =>
		new Response(
			JSON.stringify({
				content: [
					{ id: 'b1', resourceId: 'r1', resourceName: 'Sto 4, tihi dio', organizationName: 'Čitaonica PMF', timezone: 'Europe/Sarajevo', startTime: '2035-11-19T08:00:00Z', endTime: '2035-11-19T09:00:00Z', status: 'CONFIRMED', cancelDeadline: '2035-11-18T08:00:00Z' },
				],
				page: 1, size: 20, totalElements: 21, totalPages: 2,
			}),
			{ status: 200, headers: { 'Content-Type': 'application/json' } },
		),
	)
	vi.stubGlobal('fetch', fetch)

	const stranica = await mojeRezervacije(1)

	expect(fetch.mock.calls[0][0]).toBe('/api/bookings/mine?page=1&size=20')
	expect(stranica).toEqual({
		rezervacije: [
			{ id: 'b1', resurs: 'Sto 4, tihi dio', organizacija: 'Čitaonica PMF', zona: 'Europe/Sarajevo', pocetak: '2035-11-19T08:00:00Z', kraj: '2035-11-19T09:00:00Z', status: 'CONFIRMED', rokOtkazivanja: '2035-11-18T08:00:00Z' },
		],
		stranica: 1,
		ukupnoStranica: 2,
	})
})

test('otkazivanje je POST na rezervaciju i vraća novi status', async () => {
	const fetch = vi.fn(async () =>
		new Response(JSON.stringify({ id: 'b1', resourceId: 'r1', startTime: '2035-11-19T08:00:00Z', endTime: '2035-11-19T09:00:00Z', status: 'CANCELLED', expiresAt: null }), {
			status: 200,
			headers: { 'Content-Type': 'application/json' },
		}),
	)
	vi.stubGlobal('fetch', fetch)

	expect((await otkazi('b1')).status).toBe('CANCELLED')
	expect(fetch.mock.calls[0][0]).toBe('/api/bookings/b1/cancel')
	expect(fetch.mock.calls[0][1].method).toBe('POST')
})

// BookingSeriesResponse iz BookingSeriesApiTest: rezervacije po redu sedmica, svaka kao BookingResponse
test('serija vraća rezervacije po sedmicama s istim poljima kao jedna rezervacija', async () => {
	const tijelo = {
		seriesId: 's1',
		bookings: [
			{ id: 'b1', resourceId: 'r1', startTime: '2036-03-24T08:00:00Z', endTime: '2036-03-24T09:00:00Z', status: 'PENDING', expiresAt: '2036-03-21T10:00:00Z' },
			{ id: 'b2', resourceId: 'r1', startTime: '2036-03-31T07:00:00Z', endTime: '2036-03-31T08:00:00Z', status: 'PENDING', expiresAt: '2036-03-21T10:00:00Z' },
		],
	}
	vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(tijelo), { status: 201, headers: { 'Content-Type': 'application/json' } })))

	const rezervacije = await rezervisiSeriju('r1', '2036-03-24T08:00:00Z', 2, 'k-2')

	expect(rezervacije.map((r) => r.pocetak)).toEqual(['2036-03-24T08:00:00Z', '2036-03-31T07:00:00Z'])
	expect(rezervacije[0]).toEqual({ id: 'b1', pocetak: '2036-03-24T08:00:00Z', kraj: '2036-03-24T09:00:00Z', status: 'PENDING', rokPotvrde: '2036-03-21T10:00:00Z' })
})

test('serija bez liste rezervacija padne glasno', async () => {
	vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ seriesId: 's1' }), { status: 201, headers: { 'Content-Type': 'application/json' } })))

	await expect(rezervisiSeriju('r1', '2036-03-24T08:00:00Z', 2, 'k-2')).rejects.toThrow('/api/resources/r1/bookings/series: nema polja bookings')
})

