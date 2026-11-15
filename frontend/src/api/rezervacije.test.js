import { afterEach, expect, test, vi } from 'vitest'
import { postaviToken } from './apiFetch.js'
import { rezervisi } from './rezervacije.js'

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
